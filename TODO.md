# TODO

## Features

- [x] insert by chunk
- [x] Support pagination
- [x] search ignore case
- [x] Store both open and women title
- [x] Use [AppliedFragment](https://typelevel.org/skunk/reference/Fragments.html)
- [x] Add more queries (title, active, rating, etc)
- [x] Support sorting
- [x] better errors handling
- [x] support Map for getPlayersWithIds
- [ ] Pagination config (default , max , min )
- [ ] Add config to set max ids in getPlayersWithIds
- [ ] Support full text search
- [ ] Support lucene query syntax


### Better type safetye with refined/iron

- [ ] smithy type
- [ ] pagination (validate page, size and other params)
- [ ] Config

## Smithy

- [ ] Use smithy trait pattern
- [ ] Use resource
- [ ] Validators
- [ ] Error handling
- [ ] description
- [x] mixing

### Smarter Crawler (maybe include in health api)

- [ ] Cralwer info, last run, status: {running, stopped, error}
- [ ] trigger crawler by cli
- [ ] if last run is too close to now, don't run
- [ ] Crawler config

## Bugs

- [ ] App doesn't terminate when flyway migration fails
- [ ] Crawler seems doesn't stop :sweat_smile:
