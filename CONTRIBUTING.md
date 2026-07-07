# Contributing to the Chartboost Prebid Android Adapter

We value each contribution provided by the community and strive to achieve the following goals for the most optimal contribution experience:

- Maintain the adapter's quality.
- Fix problems that are important to users.
- Enable a sustainable system for the adapter's maintainers to review contributions.

Therefore, this document will note specific steps and details required for you to contribute.

## How Can I Contribute?

There are two ways to contribute:
- [Reporting Bugs](#reporting-bugs)
- [Pull Requests](#pull-requests)

### Reporting Bugs
Bugs are tracked as a type of [GitHub issues](https://guides.github.com/features/issues/). To submit a report, create an issue and provide the information instructed in the template.

> **Note:** If you find a **Closed** issue that seems similar to what you're experiencing, open a new issue and include a link to the closed issue.

Follow these guides to create a detailed and effective report for us to understand and resolve issues faster:

* **Use a clear, concise, and descriptive title** for the issue that identifies the problem.
* **Describe the steps with as much concise detail as possible to reproduce the problem**. For example, start by explaining how the Prebid ad unit was created, which renderer was registered, and the method calls invoked.
* **Provide specific examples to demonstrate each step**. Include links to files, screenshots, Charles logs, or copy/pasteable code snippets.
* **Describe the behavior(s) observed** and explain why that behavior is a problem.
* **Describe the behavior(s) expected.**
* **If you're reporting a crash**, include the crash report with a stack trace.
* **If the problem wasn't triggered by a specific action**, describe what you were doing before the problem happened.

### Pull Requests
In order to submit pull requests, you are required to review and sign the [Contribution License Agreement (CLA)](https://docs.chartboost.com/en/partners/contribution-license-agreement/) available on the Chartboost website.

- [Individual contributor license agreement form](https://na3.docusign.net/Member/PowerFormSigning.aspx?PowerFormId=159c66e8-610c-4afc-9330-15bc2217c291&env=na3&acct=9c982e12-8675-45df-9d81-95fe3656e695&v=2). _You wish to contribute on your own behalf as an individual._
- [Employer contributor license agreement form](https://na3.docusign.net/Member/PowerFormSigning.aspx?PowerFormId=73009870-c5f9-483f-b21c-eb3a222d2d6b&env=na3&acct=9c982e12-8675-45df-9d81-95fe3656e695&v=2). _You wish to contribute on behalf of your employer._

#### Submitting a Pull Request
Follow these steps to have your contribution considered by the maintainers:

1. Review and sign the [Contribution License Agreement (CLA)](https://docs.chartboost.com/en/partners/contribution-license-agreement/).
2. Identify the issue related to your fix. If an issue doesn't exist, then create a new issue.
3. Create a pull request.
4. Format the title starting with the issue number, followed by a brief description of the fix. _Example: `[ISSUE-60] Fix null pointer exception`._
5. Follow the [style guides](#style-guides) in your pull request.
6. Submit your pull request.
7. Verify that all [status checks](https://help.github.com/articles/about-status-checks/) are passing.

While the prerequisites above must be satisfied before your pull request is reviewed, the reviewer(s) may ask you to complete additional design work, tests, or other changes before your pull request can be accepted.

## Style Guides

### Git Commit Messages

* Use the present tense ("Add feature" not "Added feature")
* Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
* Limit the first line to 72 characters or less
* Reference issues and pull requests liberally after the first line

### Kotlin

Follow the Android developers [Kotlin style guide](https://developer.android.com/kotlin/style-guide).
