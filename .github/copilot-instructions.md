# AI Instructions for Autorecord App Repository

This file contains instructions for AI assistants (GitHub Copilot, Claude, etc.) working on this repository.

## Global Pull Request Workflow Rules

When creating or working on a pull request (PR):

### 1. Automatic Workflow Triggering

- **Always trigger all relevant check workflows** when creating or updating a PR
- The repository has the following automated workflows that will run automatically:
  - **PR Check Workflow** (`pr-check.yml`): Runs lint checks and build verification
    - Lint Check: Validates code quality and style
    - Build Check: Compiles debug APK and verifies successful build
  - **Android Build Workflow** (`android-build.yml`): Builds debug APK for testing
  
- These workflows are automatically triggered on:
  - Opening a new PR
  - Pushing new commits to an existing PR
  - Reopening a PR
  
- **No manual intervention required** - workflows trigger automatically via GitHub Actions

### 2. Ready for Review Status

- Once a PR is created and initial commits are pushed:
  1. Wait for all automated checks to complete (lint, build, tests)
  2. Monitor the status of all workflows in the PR
  3. If all checks pass successfully:
     - The PR should be marked as **"Ready for review"**
     - This indicates to maintainers that the PR has passed automated validation
  4. If any checks fail:
     - Review the failure logs
     - Fix the issues
     - Push corrected commits
     - Repeat until all checks pass

### 3. Workflow Verification

Before marking a PR as ready:
- ✅ Lint checks must pass
- ✅ Build must complete successfully
- ✅ All automated tests must pass (unit tests and instrumented tests)
- ✅ Test coverage meets requirements for new code
- ✅ No merge conflicts
- ✅ Code follows repository conventions

### 4. Minimal Manual Interaction

The goal is to minimize manual steps:
- Workflows trigger automatically on PR events
- Status checks update automatically
- Only manual step: marking as "Ready for review" after verifying checks pass
- All build artifacts (APKs, reports) are automatically uploaded

## Repository-Specific Context

### Android Development
- This is an Android application built with Gradle
- Java version: 17 (Temurin distribution)
- Build tool: Gradle wrapper (`./gradlew`)
- Primary build commands:
  - Lint: `./gradlew lint`
  - Build: `./gradlew assembleDebug`

### Code Quality Standards
- All code must pass Android lint checks
- Build must succeed with no errors
- APK must be generated successfully in `app/build/outputs/apk/debug/`
- Keep the code clean and follow best practices
- Remove unused functions and variables during code reviews
- Keep documentation updated and consistent with code changes

### Code Maintenance and Cleanup

When working on code changes:

#### 1. Documentation Maintenance
- **Always keep documentation updated and consistent** with code changes
- Update relevant documentation files when modifying features or APIs
- Ensure README, configuration guides, and other docs reflect current functionality
- Keep inline code comments synchronized with the actual implementation

#### 2. Code Cleanup
- **Check for and remove unused code:**
  - Identify unused functions, methods, and classes
  - Remove unused variables and imports
  - Delete commented-out code blocks (use version control instead)
  - Clean up dead code paths and unreachable code
- Use IDE or linting tools to help identify unused code
- Perform cleanup during code reviews before finalizing PRs

#### 3. Best Practices
- Follow established coding conventions and patterns in the codebase
- Keep functions focused and single-purpose
- Maintain consistent naming conventions
- Ensure proper error handling throughout the code
- Write self-documenting code with clear variable and function names
- Add comments only when necessary to explain complex logic

### Testable Code Guidelines

When implementing features, always follow these testing practices:

#### 1. Write Testable Code
- Design all code with testability in mind
- Use dependency injection to facilitate testing
- Keep functions focused and single-purpose
- Avoid tight coupling between components
- Make methods and classes easily mockable

#### 2. Create Comprehensive Tests

For **each requested feature**, create:

**a) Unit Tests:**
- Place unit tests in the `tests` folder (or `app/src/test/` for Android standard structure)
- Test individual components, methods, and classes in isolation
- Mock external dependencies
- Cover edge cases and error scenarios
- Include extensive debug output to help identify errors immediately
- Each test should have descriptive names that explain what is being tested

**b) End-to-End On-Device Tests:**
- Create instrumented tests in `app/src/androidTest/` for Android
- Test complete user workflows and interactions
- Verify actual device behavior and UI functionality
- Test integration between multiple components
- Cover realistic user scenarios

#### 3. Run Tests After Each Build

- **Always run unit tests from the tests folder after each build**
- Use command: `./gradlew test` (or appropriate test command for the project)
- Tests should be part of the development cycle, not an afterthought
- Address any test failures immediately before proceeding

#### 4. Test Output Requirements

Unit test output should include:

- **Extensive debug output:** 
  - Log test execution steps
  - Print input values and expected results
  - Show actual vs expected comparisons
  - Include stack traces for failures
  - Output intermediate calculation results when relevant
  
- **Summary at the end:**
  - Total tests run
  - Tests passed
  - Tests failed
  - Overall pass/fail status
  - Execution time
  - Quick reference to any failures

Example output format:
```
Running TestClassName...
  ✓ testMethodName1 - PASSED (0.05s)
    Input: value1
    Expected: result1
    Actual: result1
  
  ✗ testMethodName2 - FAILED (0.03s)
    Input: value2
    Expected: result2
    Actual: result3
    Error: Assertion failed at line 42

======================================
TEST SUMMARY
======================================
Total Tests: 25
Passed: 24
Failed: 1
Success Rate: 96%
Total Time: 1.23s
Status: FAILED
======================================
```

#### 5. Test Coverage Goals

- Aim for high test coverage of new code
- Critical paths should have 100% coverage
- All public APIs should have tests
- All error handling paths should be tested
- Edge cases and boundary conditions must be tested

#### 6. Testing Best Practices

- Write tests before or alongside implementation (TDD approach when possible)
- Keep tests independent and isolated
- Use meaningful test data
- Follow the AAA pattern: Arrange, Act, Assert
- Make tests deterministic (no random values without seeds)
- Clean up resources after tests (files, database, network connections)
- Use appropriate assertion messages for clarity

### Workflow Artifacts
- Lint reports are retained for 7 days
- Build APKs are available as workflow artifacts
- PR comments are automatically added with build status

## Best Practices

1. **Before creating a PR:**
   - Ensure local build passes: `./gradlew assembleDebug`
   - Run local lint: `./gradlew lint`
   - Run unit tests: `./gradlew test`
   - Review test output for any failures
   - Review changes for quality

2. **After creating a PR:**
   - Monitor workflow execution in the "Actions" tab
   - Check for any failures in the PR checks section
   - Verify all tests pass in CI/CD pipeline
   - Address failures promptly

3. **Communication:**
   - Use PR descriptions to explain changes
   - Reference related issues
   - Note any special considerations for reviewers
   - Include test results summary when relevant

## Troubleshooting Workflow Failures

If workflows fail:

1. **Check the workflow logs:**
   - Navigate to the Actions tab
   - Find the failed workflow run
   - Review detailed logs for each step

2. **Common issues:**
   - Lint errors: Review lint report artifacts
   - Build failures: Check Gradle build logs
   - Missing dependencies: Ensure `gradle.properties` is configured

3. **Fix and retry:**
   - Make necessary corrections
   - Commit and push
   - Workflows will automatically re-run

## PR Description Guidelines

When creating or updating PRs:
- Do NOT add automated tips or suggestions like "💡 You can make Copilot smarter..." to PR descriptions
- If GitHub automatically adds such sections, remove them when updating the PR description
- Keep PR descriptions focused on the actual changes made
- Include only relevant information about the code changes, testing, and impact

## Release Management

When creating releases and writing release notes:

### Release Notes Content
- **Only include user-facing changes and features** in release notes
- Focus on what matters to end users:
  - New features and capabilities
  - Bug fixes that affect user experience
  - Performance improvements users will notice
  - Breaking changes or important updates
  - UI/UX improvements

### What NOT to Include in Release Notes
- **Do NOT include** test changes or additions
- **Do NOT include** documentation updates (unless it's user-facing documentation like a user guide)
- **Do NOT include** internal code refactoring or cleanup
- **Do NOT include** dependency updates (unless they directly affect users)
- **Do NOT include** developer tooling changes
- **Do NOT include** CI/CD pipeline modifications
- **Do NOT include** internal architecture changes

### Release Notes Format
- Use clear, user-friendly language
- Group changes by category (Features, Bug Fixes, Improvements)
- Be concise but informative
- Highlight the most important changes first
- Include any necessary migration steps for users

## Notes

- These instructions apply to all PRs in this repository
- Workflows are configured in `.github/workflows/`
- For global AI instruction support across repositories, this pattern can be replicated in other repos
