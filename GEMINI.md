# Mihon Project Documentation

## Project Overview
Mihon is a comprehensive manga, comic, and novel reader application for Android, initially forked from Tachiyomi. It is built using Kotlin and follows modern Android development practices, leveraging Jetpack Compose for the UI layer, Coroutines/Flow for asynchronous operations, and a multi-module architecture.

## Core Architecture
- **app:** Main application module tying everything together, containing the legacy view-based UI and core Android components (like `ReaderActivity`).
- **core:** Foundational modules including standard utilities and archive reading logic (e.g., zip and epub parsing).
- **domain & data:** Business logic, models, and local data persistence (using SQLDelight).
- **presentation-core:** Jetpack Compose UI components.
- **source-api:** Extensions and source integration models.

## Development Instructions & Conventions
The following rules must ALWAYS be followed when making modifications to this codebase:

1. **Idiomatic Kotlin:** Use idiomatic Kotlin constructs. Avoid unnecessary mutability, leverage standard library functions (`map`, `filter`, `let`, `apply`), and ensure null safety.
2. **Coroutines & Flows:** Always use Kotlin Coroutines for asynchronous work. Use appropriate dispatchers (`Dispatchers.IO` for disk/network operations, `Dispatchers.Main` or `activity.runOnUiThread` for UI updates). Do not block the main thread.
3. **Lazy Loading & Memory Management:** Be cautious with memory-intensive operations. For large resources (like reading ZIP/ePub entries or large images), implement deferred loading, pagination, or sliding window strategies to avoid OOM exceptions and application freezing.
4. **Jetpack Compose:** When modifying modern UI layers, adhere strictly to Compose state management principles. Keep composables stateless where possible and hoist state.
5. **No Hacky Workarounds:** Avoid bypassing type safety, disabling linters, or using reflection unless absolutely necessary. Solve architectural issues through proper delegation and design patterns.
6. **Testing:** Maintain existing testing conventions and ensure new logic is covered if applicable.

*(Note: No project issues or backlog should be maintained in this file.)*
