# Manual releases

Do not run a release while image work is paused. Source commits and PRs do not build images.

When the owner explicitly requests a release, use a unique reviewed tag. Required tools: Docker/buildx, Git, Python 3, AWS CLI for existing public ECR authentication. The registry is `public.ecr.aws/x9d4j8f0`; this repository does not assume access to the clone's private ECR account.

```sh
aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws
scripts/build-images.sh YYYY-MM-DD-description --push
```

The script compiles sources first, then publishes amd64/arm64 backend and frontend images; unchanged DB/gateway manifest digests are retagged. It does not deploy. It refuses an invalid tag. Do not overwrite an existing release tag. Clean or relocate `.build/esm-admin-tools` before a new frontend preparation; the script refuses to overwrite developer edits.

Alternatively explicitly dispatch **Manual reference release** with `publish=true`. Configure the `reference-release` environment and its `REFERENCE_AWS_ACCESS_KEY_ID` / `REFERENCE_AWS_SECRET_ACCESS_KEY` secrets first; these are not stored here. Default `publish=false` runs validation only. Do not add push or pull_request triggers to the release workflow. No release workflow was dispatched during initial repository setup.

After publication, inspect each image's registry digest and platforms, save a release manifest in `releases/`, and test that release on an isolated QA stack. Record actual headless/API coverage, including edge cases, rather than assuming compilation proves runtime behavior. Then QA may use `scripts/qa-update.sh TAG`.

## Seed and compatibility data

The owner selected the **large existing dataset**. Keep it in the digest-pinned DB image/existing named volume. Do not replace it with clone `seed-git.db`, upload a DB dump to Git, or implicitly reset volumes. App-only release scripts do not rebuild the DB image. `docker/db` preserves the prior idempotent compatibility SQL and entrypoint for separately reviewed DB maintenance. The `06-legacy-qa.sql` patch covers theme, retention, concept mappings and compatibility settings; it is not the seed. The clone's dataset alignment is managed separately.

Any later DB rebuild requires an explicit artifact source, checksum, compatible schema, and owner authorization. Use a private artifact store for such inputs; never commit artifacts, credentials or `.env` files. A Git pull cannot transport or migrate a database.
