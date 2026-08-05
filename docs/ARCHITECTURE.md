# Architecture

## Modules

- `app`: Android application, UI, Room databases, search repository and TTS.
- `dict-builder`: desktop Java tool for validating and converting licensed source data.

## Layers

- `ui`: Activity, Fragments, adapters and ViewModels.
- `data/dictionary`: immutable dictionary entities, DAO and seed loader.
- `data/user`: favorites and search history database.
- `domain/search`: query classification, pinyin normalization, stroke parsing and ranking.
- `speech`: application-scoped TextToSpeech manager.

## Database boundary

`dictionary.db` can be replaced when dictionary data is upgraded. `user.db` contains only user-created state and is migrated independently, preventing dictionary updates from deleting favorites or history.

## Threading

All database access and seed parsing run on `AppExecutors.io()`. Search requests carry a generation number so stale results cannot overwrite a newer query.

