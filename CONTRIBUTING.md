# Contributing to Knit

Thank you for your interest in contributing to Knit! We welcome contributions of all kinds, including bug reports, feature requests, and code contributions.

## How Can I Contribute?

### Reporting Bugs

If you find a bug, please open an issue on GitHub. Before creating an issue, please search for existing issues to avoid duplication. Here are some guidelines for creating a good bug report:
- Use a clear and descriptive title for the issue to identify the problem.
- Provide detailed steps to reproduce the bug and include any relevant code or error messages.
- Specify the expected behavior and the actual behavior.
- Include information about your environment, such as the version of Knit and the platform you're using.

### Suggesting Features

We welcome suggestions for new features and improvements. If you have an idea for a new feature, please open an issue on GitHub, please:
- Specify the feature you'd like to see.
- Provide a detailed explanation of why the feature would be useful.
- Include any relevant use cases or examples.

## Pull Requests

Environment Setup:
- JDK 17
- IDEA 2024.x or higher

When you're ready to submit a pull request, please follow these steps:
- Make sure you well know the Knit usages and principles.
    - Check [the Advance Usages](docs/README.md) document for advance usages.
    - Check [the Principles](docs/Principle_of_Dependency_Lookup.md) for Knit dependency lookup principles.
    - Check [Knit-ASM workflow](knit-asm/asm-internal.png) to know the Knit asm process.
- Fork the repository and create a new branch for your changes.
- Make your changes and commit them with clear and concise commit messages.
- Ensure that your code passes all tests and is well-documented.
    - Make sure your changes are covered by unit tests by using `./gradlew :knit-asm:test`

## Code of Conduct

Please note that all participants in this project are expected to uphold our [Code of Conduct](CODE_OF_CONDUCT.md). By
participating, you agree to abide by its terms.

We're excited to see your contributions! Thank you!
