package org.openmrs.module.reporting.data.obs.persister;

import org.openmrs.annotation.Handler;
import org.openmrs.module.reporting.data.obs.definition.ObsDataDefinition;
import org.openmrs.module.reporting.definition.persister.SerializedDefinitionPersister;

/** Uses the existing serialized-object store for editable object dataset sources. */
@Handler(supports={ObsDataDefinition.class})
public class SerializedObsDataPersister extends SerializedDefinitionPersister<ObsDataDefinition> {
    protected SerializedObsDataPersister() { }
    @Override public Class<ObsDataDefinition> getBaseClass() { return ObsDataDefinition.class; }
}
