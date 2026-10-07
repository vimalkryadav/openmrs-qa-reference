package org.openmrs.module.reporting.serializer;

import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.mapper.Mapper;
import org.openmrs.module.reporting.definition.DefinitionContext;
import org.openmrs.module.reporting.definition.service.DefinitionService;
import org.openmrs.module.reporting.evaluation.Definition;

/** Saved object data/query mappings resolve their UUID using the same path as patient data. */
public class ObjectDefinitionConverter extends ReportingShortConverter {
    private final Class<? extends Definition> type;
    public ObjectDefinitionConverter(Mapper mapper, ConverterLookup lookup, Class<? extends Definition> type) {
        super(mapper,lookup); this.type=type;
    }
    @Override public Class<? extends Definition> getDefinitionType() { return type; }
    @Override public DefinitionService<?> getDefinitionService() { return DefinitionContext.getDefinitionService(type); }
}
