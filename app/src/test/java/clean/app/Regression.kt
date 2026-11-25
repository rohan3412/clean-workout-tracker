package clean.app

/**
 * JUnit 4 category for the regression suite that GitHub Actions runs before it builds the APKs
 * (`./gradlew :app:testDebugUnitTest -Pregression`): tests that guard a bug or complaint once
 * fixed, are deterministic and do not depend on the machine. Put `@Category(Regression::class)` on a test class or method.
 */
interface Regression

/** Marks a test that only draws pictures of the screens; the regression suite leaves these out even inside a Regression class. */
interface Tour
