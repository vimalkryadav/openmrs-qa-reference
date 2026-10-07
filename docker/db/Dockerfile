FROM public.ecr.aws/x9d4j8f0/openmrs-qa-db@sha256:51ecf24abccae9785894ea400f9de502aee2278980aba8ee911d69b64a95c38c
COPY 06-legacy-qa.sql /opt/legacy-qa/06-legacy-qa.sql
COPY legacy-entrypoint.sh /opt/legacy-qa/entrypoint.sh
ENTRYPOINT ["/bin/sh", "/opt/legacy-qa/entrypoint.sh"]
CMD ["mariadbd"]
HEALTHCHECK --interval=5s --timeout=5s --start-period=60s --retries=120 CMD test -f /var/lib/mysql-qa/.legacy-qa-20261007 && healthcheck.sh --defaults-extra-file=/var/lib/mysql-qa/.my-healthcheck.cnf --connect --innodb_initialized
LABEL org.opencontainers.image.version="2026-10-07-legacy-qa"
