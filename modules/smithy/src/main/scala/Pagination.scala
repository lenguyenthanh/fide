package fide.spec

import cats.*
import cats.syntax.all.*
import fs2.Stream
import smithy.api.Paginated
import smithy4s.kinds.FunctorAlgebra
import smithy4s.schema.Primitive.PString
import smithy4s.schema.{ Field, Primitive, Schema, SchemaVisitor }
import smithy4s.{ Hints, Service, ShapeId }

object Pagination:

  // scalafmt: {maxColumn = 120}
  def apply[Interface[_[_, _, _, _, _]], F[_]: Monad](
      impl: FunctorAlgebra[Interface, F]
  )(implicit service: Service[Interface]): FunctorAlgebra[Interface, Stream[F, *]] =
    val serviceFunction = service.toPolyFunction(impl)
    val endpointCompiler =
      // type X[A, B] = (A, Token) => B
      // def x[A]: SchemaVisitor.Default[X[A, *]] = ???
      new service.FunctorEndpointCompiler[Stream[F, *]]:
        def apply[Input, Error, Output, SI, SO](
            endpoint: service.Endpoint[Input, Error, Output, SI, SO]
        ): Input => Stream[F, Output] =
          endpoint.hints match
            case Paginated.hint(Paginated(Some(inputToken), Some(outputToken), _, _)) =>
              paginate[F, Input, Output, String](
                (input: Input) => serviceFunction(endpoint.wrap(input)),
                getTokenFromOutput = endpoint.output.compile(GetTokenVisitor(outputToken.value)),
                putTokenInInput = endpoint.input.compile[SetToken](SetTokenVisitor(inputToken.value))
              )
            case _ => input => Stream.eval(serviceFunction(endpoint.wrap(input)))

    service.impl(endpointCompiler)

  type Token               = String
  type SetToken[A]         = (A, Token) => A
  type ReplaceWithToken[A] = Token => A

  // This visitor aims at providing a replacement function that will take care of taking the token
  // and provide the value expected by a specific field of our input structure.
  object ReplaceWithTokenVisitor extends SchemaVisitor.Optional[ReplaceWithToken]:
    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): Option[ReplaceWithToken[P]] =
      tag match
        case PString => Some(identity[String])
        case _       => None

    override def option[A](schema: Schema[A]): Option[ReplaceWithToken[Option[A]]] =
      val maybeReplaceWithTokenA: Option[ReplaceWithToken[A]] = schema.compile(this)
      maybeReplaceWithTokenA.map { replaceWithTokenA => token => Some(replaceWithTokenA(token)) }

  class SetTokenVisitor(tokenField: String) extends SchemaVisitor.Default[SetToken]:
    def default[A]: SetToken[A] = (a, _) => a

    def compileField[S, A](field: Field[S, A]): (S, Token) => A =
      if field.label == tokenField then
        val maybeReplace: Option[Token => A] = field.schema.compile(ReplaceWithTokenVisitor)
        maybeReplace match
          case Some(replace) => (s: S, token: Token) => replace(token)
          case None          => (s: S, token: Token) => field.get(s)
      else (s: S, token: Token) => field.get(s)

    override def struct[S](
        shapeId: ShapeId,
        hints: Hints,
        fields: Vector[Field[S, ?]],
        make: IndexedSeq[Any] => S
    ): SetToken[S] =
      val updateFunctions = fields.map(compileField(_))
      (s: S, token: Token) => make(updateFunctions.map(update => update(s, token)))

  type GetToken[A] = A => Option[Token]
  class GetTokenVisitor(tokenField: String) extends SchemaVisitor.Default[GetToken]:
    def default[A]: GetToken[A] = _ => None

    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): GetToken[P] = tag match
      case PString => (string: String) => Some(string)
      case _       => _ => None

    override def option[A](schema: Schema[A]): GetToken[Option[A]] =
      val getTokenA = schema.compile(this)
      (maybeA: Option[A]) => maybeA.flatMap(getTokenA)

    def compileField[S, A](field: Field[S, A]): GetToken[S] =
      val getTokenA = field.schema.compile(this)
      (s: S) => getTokenA(field.get(s))

    override def struct[S](
        shapeId: ShapeId,
        hints: Hints,
        fields: Vector[Field[S, ?]],
        make: IndexedSeq[Any] => S
    ): GetToken[S] =
      fields.find(_.label == tokenField) match
        case Some(field) => compileField(field)
        case None        => (_: S) => None

  def paginate[F[_]: Monad, Input, Output, Token](
      f: Input => F[Output],
      getTokenFromOutput: Output => Option[Token],
      putTokenInInput: (Input, Token) => Input
  ): Input => Stream[F, Output] = (firstInput: Input) =>
    Stream.eval(f(firstInput)).flatMap { firstOutput =>
      val firstInputOutput = (firstInput, firstOutput)
      val unfoldEval = Stream.unfoldEval(firstInputOutput) { case (currentInput, currentOutput) =>
        val maybeNextInput =
          getTokenFromOutput(currentOutput)
            .map(putTokenInInput(currentInput, _))
        maybeNextInput
          .traverse(newInput =>
            f(newInput).map { newOutput =>
              val newInputOutput = (newInput, newOutput)
              (newOutput, newInputOutput)
            }
          )
      }
      Stream(firstOutput) ++ unfoldEval
    }
