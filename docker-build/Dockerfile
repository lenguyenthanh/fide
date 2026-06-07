# Self-contained Alpine image for the native fide-cli.
#
# The binary is a glibc binary linked on the Ubuntu CI runner, so Alpine needs
# `gcompat` (glibc compatibility shim) plus the TLS stack the Scala Native build
# links against: s2n-tls, utf8proc, and openssl's libcrypto.so.3 (libcrypto3).
# Mirrors rolang/dumbo's docker-build, with libcrypto3 added because our s2n keg
# links openssl@3 (dumbo's s2n self-links its crypto).
FROM alpine:latest

RUN apk update && apk add --no-cache \
      gcompat \
      libgcc \
      libstdc++ \
      s2n-tls \
      utf8proc \
      libcrypto3

ADD --chmod=+x fide-cli-linux-x86_64 /usr/local/bin/fide-cli

ENTRYPOINT ["/usr/local/bin/fide-cli"]
CMD ["--help"]
