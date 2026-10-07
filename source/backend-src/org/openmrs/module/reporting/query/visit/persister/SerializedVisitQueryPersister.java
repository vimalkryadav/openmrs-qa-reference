package org.openmrs.module.reporting.query.visit.persister;

import org.openmrs.annotation.Handler;
import org.openmrs.module.reporting.query.visit.definition.VisitQuery;
import org.openmrs.module.reporting.definition.persister.SerializedDefinitionPersister;

/** Uses the existing serialized-object store for editable object dataset sources. */
@Handler(supports={VisitQuery.class})
public class SerializedVisitQueryPersister extends SerializedDefinitionPersister<VisitQuery> {
    protected SerializedVisitQueryPersister() { }
    @Override public Class<VisitQuery> getBaseClass() { return VisitQuery.class; }
}
