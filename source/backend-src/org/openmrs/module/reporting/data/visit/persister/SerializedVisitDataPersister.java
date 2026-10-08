package org.openmrs.module.reporting.data.visit.persister;

import org.openmrs.annotation.Handler;
import org.openmrs.module.reporting.data.visit.definition.VisitDataDefinition;
import org.openmrs.module.reporting.definition.persister.SerializedDefinitionPersister;

/** Uses the existing serialized-object store for editable object dataset sources. */
@Handler(supports={VisitDataDefinition.class})
public class SerializedVisitDataPersister extends SerializedDefinitionPersister<VisitDataDefinition> {
    protected SerializedVisitDataPersister() { }
    @Override public Class<VisitDataDefinition> getBaseClass() { return VisitDataDefinition.class; }
}
