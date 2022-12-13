## Generate Github Token Token

[Generate a token](https://github.com/settings/tokens/new) with scope _Access public repositories_ `public_repo`.

## Run

Run with:

```
GITHUB_TOKEN=ghp_.... ./gradlew run  
```

Will print module name (in green if master is on 4.0.0-SNAPSHOT), a tick if the latest Java CI is passing (a cross if failing), and the version of micronaut that the master branch is using. 