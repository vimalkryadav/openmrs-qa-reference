package org.openmrs.module.reporting.web.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.*;
import org.openmrs.module.htmlwidgets.web.WidgetUtil;
import org.openmrs.module.reporting.common.SortCriteria;
import org.openmrs.module.reporting.data.DataDefinition;
import org.openmrs.module.reporting.data.MappedData;
import org.openmrs.module.reporting.data.converter.DataConverter;
import org.openmrs.module.reporting.dataset.column.definition.RowPerObjectColumnDefinition;
import org.openmrs.module.reporting.dataset.definition.RowPerObjectDataSetDefinition;
import org.openmrs.module.reporting.definition.DefinitionContext;
import org.openmrs.module.reporting.evaluation.Definition;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.evaluation.parameter.Parameterizable;
import org.openmrs.module.reporting.evaluation.parameter.ParameterizableUtil;

/** Named, mapped object columns and sorting for legacy annotated dataset editors. */
public final class RowObjectDefinitionEditor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RowObjectDefinitionEditor() { }

    public static boolean supports(Definition definition, String field) {
        return definition instanceof RowPerObjectDataSetDefinition
                && Arrays.asList("columnDefinitions", "sortCriteria", "rowFilters").contains(field);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static String model(RowPerObjectDataSetDefinition definition) throws Exception {
        Map<String,Object> model = new LinkedHashMap<String,Object>();
        List<Map<String,Object>> sources = new ArrayList<Map<String,Object>>();
        Set<String> seen = new HashSet<String>();
        for (Class<? extends DataDefinition> type : definition.getSupportedDataDefinitionTypes()) {
            for (Definition value : DefinitionContext.getAllDefinitions(type, false)) {
                if (seen.add(value.getUuid())) sources.add(source(value));
            }
        }
        model.put("definitions", sources);
        model.put("converterCatalogue", ConvertedDefinitionEditor.catalogue());
        List<Map<String,Object>> columns = new ArrayList<Map<String,Object>>();
        for (RowPerObjectColumnDefinition column : definition.getColumnDefinitions()) {
            Map<String,Object> row = mapped(column.getDataDefinition());
            row.put("name", column.getName());
            DataDefinition source = column.getDataDefinition().getParameterizable();
            // Core addColumn wraps cross-object data definitions; expose the saved inner source.
            while (!seen.contains(source.getUuid())) {
                Method getter;
                try { getter = source.getClass().getMethod("getJoinedDefinition"); }
                catch (NoSuchMethodException absent) { break; }
                Object inner = getter.invoke(source);
                if (!(inner instanceof DataDefinition) || inner == source) break;
                source = (DataDefinition) inner;
            }
            row.put("uuid", source.getUuid());
            List<Object> converters = new ArrayList<Object>();
            for (DataConverter converter : column.getDataDefinition().getConverters() == null ? Collections.<DataConverter>emptyList() : column.getDataDefinition().getConverters()) {
                converters.add(ConvertedDefinitionEditor.describe(converter));
            }
            row.put("converters", converters);
            columns.add(row);
        }
        model.put("columns", columns);
        List<Object> sorts = new ArrayList<Object>();
        if (definition.getSortCriteria() != null) {
            for (SortCriteria.SortElement element : definition.getSortCriteria().getSortElements()) {
                Map<String,Object> row = new LinkedHashMap<String,Object>();
                row.put("column", element.getElementName()); row.put("direction", element.getDirection().name());
                sorts.add(row);
            }
        }
        model.put("sorts", sorts);
        List<Object> filters = new ArrayList<Object>();
        List<Object> filterSources = new ArrayList<Object>();
        try {
            Class<? extends Definition> type = (Class) ParameterizableUtil.getMappedType(definition.getClass(), "rowFilters");
            for (Definition value : DefinitionContext.getAllDefinitions(type, false)) filterSources.add(source(value));
            for (Mapped<?> filter : (List<Mapped<?>>) definition.getClass().getMethod("getRowFilters").invoke(definition)) filters.add(mapped(filter));
        } catch (NoSuchMethodException absent) { /* Dataset without row filters. */ }
        model.put("filters", filters); model.put("filterDefinitions", filterSources);
        return JSON.writeValueAsString(model);
    }

    private static Map<String,Object> source(Definition value) {
        Map<String,Object> row = new LinkedHashMap<String,Object>();
        row.put("uuid",value.getUuid()); row.put("name",value.getName());
        List<Object> parameters = new ArrayList<Object>();
        for (Parameter p : value.getParameters()) {
            Map<String,Object> parameter = new LinkedHashMap<String,Object>();
            parameter.put("name",p.getName()); parameter.put("label",p.getLabel());
            parameter.put("type",p.getType().getName());
            parameter.put("collectionType",p.getCollectionType() == null ? null : p.getCollectionType().getName()); parameters.add(parameter);
        }
        row.put("parameters",parameters); return row;
    }

    private static Map<String,Object> mapped(Mapped<?> mapped) {
        Map<String,Object> row = new LinkedHashMap<String,Object>();
        if (mapped != null && mapped.getParameterizable() != null) {
            row.put("uuid",mapped.getParameterizable().getUuid());
            Map<String,Object> values = new LinkedHashMap<String,Object>();
            if (mapped.getParameterMappings() != null) for (Map.Entry<String,Object> entry : mapped.getParameterMappings().entrySet()) values.put(entry.getKey(), inputValue(entry.getValue()));
            row.put("mappings",values);
        }
        return row;
    }

    private static Object inputValue(Object value) {
        if (value instanceof Date) return org.openmrs.api.context.Context.getDateFormat().format((Date)value);
        if (value instanceof org.openmrs.OpenmrsObject) return String.valueOf(((org.openmrs.OpenmrsObject)value).getId());
        if (value instanceof Collection) {
            List<Object> values = new ArrayList<Object>();
            for (Object item : (Collection<?>)value) values.add(inputValue(item));
            return values;
        }
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object parse(RowPerObjectDataSetDefinition definition, String field, String input) {
        try {
            List<Map<String,Object>> rows = input == null || input.trim().isEmpty()
                    ? new ArrayList<Map<String,Object>>() : JSON.readValue(input, List.class);
            if ("sortCriteria".equals(field)) {
                SortCriteria criteria = new SortCriteria();
                for (Map<String,Object> row : rows) criteria.addSortElement((String)row.get("column"), SortCriteria.SortDirection.valueOf((String)row.get("direction")));
                return criteria;
            }
            if ("rowFilters".equals(field)) {
                List<Mapped<?>> filters = new ArrayList<Mapped<?>>();
                Class<? extends Definition> type = (Class)ParameterizableUtil.getMappedType(definition.getClass(), "rowFilters");
                for (Map<String,Object> row : rows) {
                    Definition source = DefinitionContext.getDefinitionByUuid(type, (String)row.get("uuid"));
                    if (source == null) throw new IllegalArgumentException("Choose an existing row filter");
                    filters.add(new Mapped(source, mappings(source, row)));
                }
                return filters;
            }
            RowPerObjectDataSetDefinition draft = definition.getClass().newInstance();
            Set<String> names = new HashSet<String>();
            for (Map<String,Object> row : rows) {
                String name = (String)row.get("name");
                if (name == null || name.trim().isEmpty() || !names.add(name.trim())) throw new IllegalArgumentException("Column names must be nonempty and unique");
                DataDefinition source = null;
                for (Class<? extends DataDefinition> type : definition.getSupportedDataDefinitionTypes()) {
                    source = DefinitionContext.getDefinitionByUuid(type, (String)row.get("uuid"));
                    if (source != null) break;
                }
                if (source == null) throw new IllegalArgumentException("Choose an existing compatible data definition");
                List<DataConverter> converters = (List<DataConverter>)ConvertedDefinitionEditor.parse(definition,"converters",JSON.writeValueAsString(row.get("converters") == null ? Collections.emptyList() : row.get("converters")));
                draft.addColumn(name.trim(), source, null, converters.toArray(new DataConverter[converters.size()]));
                draft.getColumnDefinition(name.trim()).getDataDefinition().setParameterMappings(mappings(source,row));
            }
            return draft.getColumnDefinitions();
        } catch (Exception error) {
            if (error instanceof IllegalArgumentException) throw (IllegalArgumentException)error;
            throw new IllegalArgumentException("Invalid object dataset configuration: " + error.getMessage(),error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> mappings(Parameterizable source, Map<String,Object> row) {
        Map<String,Object> submitted = (Map<String,Object>)row.get("mappings");
        Map<String,Object> mappings = new LinkedHashMap<String,Object>();
        for (Parameter parameter : source.getParameters()) {
            Object value = submitted == null ? null : submitted.get(parameter.getName());
            if (value != null && !(value instanceof String && ((String)value).startsWith("${"))) {
                if (parameter.getCollectionType() != null && !(value instanceof Collection)) throw new IllegalArgumentException("Enter a JSON list for " + parameter.getName());
                if (value instanceof Collection) {
                    Collection<Object> parsed = Set.class.isAssignableFrom(parameter.getCollectionType()) ? new LinkedHashSet<Object>() : new ArrayList<Object>();
                    for (Object item : (Collection<?>)value) parsed.add(parseValue(item,parameter.getType()));
                    value = parsed;
                } else value = parseValue(value,parameter.getType());
            }
            if (parameter.isRequired() && (value == null || "".equals(value) || value instanceof Collection && ((Collection<?>)value).isEmpty())) throw new IllegalArgumentException("A value is required for " + parameter.getName());
            if (value != null) mappings.put(parameter.getName(),value);
        }
        return mappings;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseValue(Object input, Class<?> type) {
        if (input == null) return null;
        Object value;
        // htmlwidgets has no registered handler for some metadata, including VisitType.
        if (org.openmrs.OpenmrsObject.class.isAssignableFrom(type)) {
            value = org.openmrs.api.context.Context.getService(org.openmrs.module.htmlwidgets.service.HtmlWidgetsService.class).getObject((Class)type,Integer.valueOf(input.toString()));
            if (value == null) throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value");
        } else value = WidgetUtil.parseInput(input,type,null);
        if (value != null && !type.isInstance(value)) throw new IllegalArgumentException("Unsupported mapped value type " + type.getSimpleName());
        return value;
    }

    public static void validate(RowPerObjectDataSetDefinition definition) {
        Set<String> names = new HashSet<String>();
        for (RowPerObjectColumnDefinition column : definition.getColumnDefinitions()) names.add(column.getName());
        if (definition.getParameter("columnDefinitions") == null && definition.getSortCriteria() != null) {
            Set<String> sorted = new HashSet<String>();
            for (SortCriteria.SortElement element : definition.getSortCriteria().getSortElements()) {
                if (!names.contains(element.getElementName()) || !sorted.add(element.getElementName())) throw new IllegalArgumentException("Sort rules must reference distinct existing columns");
            }
        }
    }
}
