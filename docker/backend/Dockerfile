FROM public.ecr.aws/x9d4j8f0/openmrs-qa-backend@sha256:6445f410681cabf2badd5c0d4dd07a5b3f56080c9732827c0541f2c8dddddf5e
USER root
COPY cohort-3.7.3.omod /openmrs/distribution/openmrs_modules/cohort-3.7.3.omod
COPY startup.sh /openmrs/startup.sh
COPY logic-0.5.5.omod /openmrs/distribution/openmrs_modules/logic-0.5.5.omod
COPY reporting-2.1.0.omod /openmrs/distribution/openmrs_modules/reporting-2.1.0.omod
COPY legacyui-2.1.0.omod /openmrs/distribution/openmrs_modules/legacyui-2.1.0.omod
COPY patientdocuments-1.1.0.omod /openmrs/distribution/openmrs_modules/patientdocuments-1.1.0.omod
COPY core/ /tmp/reports-core/
RUN cd /tmp/reports-core && jar uf /openmrs/distribution/openmrs_core/openmrs.war WEB-INF && rm -rf /tmp/reports-core
COPY openconceptlab/ /openmrs/distribution/openmrs_owas/openconceptlab/
RUN chmod -R a+rX /openmrs/distribution/openmrs_owas/openconceptlab
ARG RELEASE_TAG
LABEL org.opencontainers.image.version=$RELEASE_TAG
USER 1001
