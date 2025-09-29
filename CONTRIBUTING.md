# Contributing

Thank you for considering contributing to KGraphQL!
All contributions are welcome, feel free to open issues and PRs or reach out via Slack.

## Getting Started

To perform a local build you only need to have a recent JDK installed and invoke

```bash
./gradlew build
```

You can verify your changes using

```bash
./gradlew check
```

Please also ensure that the existing examples are still working, or adapt them as necessary.

## Creating a Pull Request

Pull requests are the best way of getting your changes into the code base.
Before opening a pull request for non-trivial changes, it might be good to open an issue for discussion first to
avoid unnecessary work, although this is not required.

To support reviewing your changes, please

- Target the `main` branch
- Submit **one** pull request per bug or feature to keep the changes small and concise
- Follow existing code style and conventions
- Add automated tests when possible
- Structure your changes in as little _meaningful_ commits as possible. Don't be shy to force-push if needed.

## Writing Commit Messages

Commit messages matter. Ideally, your commit has

* A short subject line with a prefix according
  to [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/),
  not longer than 50 characters, and
* A descriptive body explaining **what** and **why** you did, separated by a blank line

This will automatically pre-fill the pull request title and description with meaningful
content.

Example:

```text
Summarize changes in around 50 characters or less

More detailed explanatory text, if necessary. Wrap it to about 72
characters or so. In some contexts, the first line is treated as the
subject of the commit and the rest of the text as the body. The
blank line separating the summary from the body is critical (unless
you omit the body entirely); various tools like `log`, `shortlog`
and `rebase` can get confused if you run the two together.

Explain the problem that this commit is solving. Focus on why you
are making this change as opposed to how (the code explains that).
Are there side effects or other unintuitive consequences of this
change? Here's the place to explain them.

Further paragraphs come after blank lines.

 - Bullet points are okay, too

 - Typically a hyphen or asterisk is used for the bullet, preceded
   by a single space, with blank lines in between, but conventions
   vary here

If you use an issue tracker, put references to them at the bottom,
like this:

Resolves: #123
See also: #456, #789
```

See [How to Write a Git Commit Message](https://cbea.ms/git-commit/) for a more thorough explanation.

## Developer Certificate of Origin

Lastly, the boring (but unfortunately important) legal part.

Tl;dr: you guarantee that you did not simply copy someone else's code without permission.

```
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.


Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```
