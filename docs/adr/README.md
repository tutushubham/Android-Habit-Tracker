# Architecture decision records

Short records of decisions that shape the code, so later changes know what was weighed. One file per decision,
numbered in order; a superseded record stays and points to its replacement.

| # | Decision | Status |
|---|----------|--------|
| [0001](0001-google-sheet-as-backend.md) | The person's own Google Sheet is the only shared store; no backend of ours | accepted |
| [0002](0002-lifecycle-viewmodels.md) | Multiplatform `androidx.lifecycle.ViewModel`s owned by `AppGraph` | accepted |
| [0003](0003-navigation-back-stack.md) | A small hierarchical back stack instead of Navigation Compose | accepted |
| [0004](0004-no-di-framework.md) | Hand-written composition root (`AppGraph`), no DI framework | accepted |
