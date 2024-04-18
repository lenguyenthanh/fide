# TODO

## Features

- [ ] Add docs for API https://smithy.io/2.0/spec/idl.html#comments
- [ ] Sanitize player's names
- [ ] Pagination config (default , max , min )
- [ ] Add config to set max ids in getPlayersWithIds
- [ ] Store more information for players as legends say: https://ratings.fide.com/download_lists.phtml
- [ ] Federation API
- [ ] Support full text search
- [ ] Store history of rating
- [ ] Support lucene query syntax
- [x] insert by chunk
- [x] Support pagination
- [x] search ignore case
- [x] Store both open and women title
- [x] Use [AppliedFragment](https://typelevel.org/skunk/reference/Fragments.html)
- [x] Add more queries (title, active, rating, etc)
- [x] Support sorting
- [x] better errors handling
- [x] support Map for getPlayersWithIds
- [x] Deploy to heroku
- [x] pagination (validate page, size and other params)

### Better type safe with refined/iron

- [ ] [smithy4s refinements](https://disneystreaming.github.io/smithy4s/docs/codegen/customisation/refinements)
- [ ] Config

## Smithy

- [x] mixing
- [x] Error handling
- [ ] description
- [ ] Use smithy trait pattern
- [ ] Use resource
- [ ] Validators

### Smarter Crawler (maybe include in health api)

- [ ] trigger crawler by cli
- [x] Cralwer info, last run, status: {running, stopped, error}
- [x] if last run is too close to now, don't run
- [x] Crawler config

## Bugs

- [ ] App doesn't terminate when flyway migration fails
- [x] Crawler seems doesn't stop :sweat_smile:
