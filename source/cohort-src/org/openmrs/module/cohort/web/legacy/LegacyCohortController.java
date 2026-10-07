/*
 * Decompiled with CFR 0.152.
 */
package org.openmrs.module.cohort.web.legacy;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.hibernate.SessionFactory;
import org.openmrs.BaseOpenmrsMetadata;
import org.openmrs.BaseOpenmrsObject;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.attribute.BaseAttribute;
import org.openmrs.attribute.BaseAttributeType;
import org.openmrs.module.cohort.CohortAttribute;
import org.openmrs.module.cohort.CohortAttributeType;
import org.openmrs.module.cohort.CohortM;
import org.openmrs.module.cohort.CohortMember;
import org.openmrs.module.cohort.CohortMemberAttribute;
import org.openmrs.module.cohort.CohortMemberAttributeType;
import org.openmrs.module.cohort.CohortType;
import org.openmrs.module.cohort.api.CohortMemberService;
import org.openmrs.module.cohort.api.CohortService;
import org.openmrs.module.cohort.api.CohortTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class LegacyCohortController {
    private static final int LIMIT = 1000;
    private static final String FULL_NAME = "TRIM(CONCAT_WS(' ',NULLIF(pn.prefix,''),NULLIF(pn.given_name,''),NULLIF(pn.middle_name,''),NULLIF(pn.family_name_prefix,''),NULLIF(pn.family_name,''),NULLIF(pn.family_name2,''),NULLIF(pn.family_name_suffix,''),NULLIF(pn.degree,'')))";
    private static final String NAME_JOIN = " JOIN person_name pn ON pn.person_name_id=(SELECT n.person_name_id FROM person_name n WHERE n.person_id=p.person_id AND n.voided=0 ORDER BY n.preferred DESC,n.person_name_id LIMIT 1) ";

    private CohortService cohorts() {
        return Context.getService(CohortService.class);
    }

    private CohortMemberService members() {
        return Context.getService(CohortMemberService.class);
    }

    private CohortTypeService types() {
        return Context.getService(CohortTypeService.class);
    }

    private String value(HttpServletRequest httpServletRequest, String string) {
        String string2 = httpServletRequest.getParameter(string);
        return string2 == null ? "" : string2.trim();
    }

    private static Map<String, Object> map(Object ... objectArray) {
        LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
        for (int i = 0; i < objectArray.length; i += 2) {
            linkedHashMap.put((String)objectArray[i], objectArray[i + 1]);
        }
        return linkedHashMap;
    }

    private List<Map<String, Object>> query(String string, Object ... objectArray) {
        Context.requirePrivilege("View Cohorts In Cohort Module");
        SessionFactory sessionFactory = Context.getRegisteredComponent("sessionFactory", SessionFactory.class);
        return sessionFactory.getCurrentSession().doReturningWork(connection -> {
            ArrayList arrayList = new ArrayList();
            try (PreparedStatement preparedStatement = connection.prepareStatement(string);){
                for (int i = 0; i < objectArray.length; ++i) {
                    preparedStatement.setObject(i + 1, objectArray[i]);
                }
                try (ResultSet resultSet = preparedStatement.executeQuery();){
                    ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
                    while (resultSet.next()) {
                        LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
                        for (int i = 1; i <= resultSetMetaData.getColumnCount(); ++i) {
                            linkedHashMap.put(resultSetMetaData.getColumnLabel(i), resultSetMetaData.getColumnType(i) == 93 ? resultSet.getTimestamp(i) : (resultSetMetaData.getColumnType(i) == 91 ? resultSet.getDate(i) : resultSet.getObject(i)));
                        }
                        arrayList.add(linkedHashMap);
                    }
                }
            }
            return arrayList;
        });
    }

    private static final int COHORT_PAGE_SIZE = 25;

    private int pageNumber(HttpServletRequest request) {
        String page = value(request, "resultPage");
        if (page.isEmpty()) return 0;
        try {
            int number = Integer.parseInt(page);
            if (number < 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid result page");
        }
    }

    private String pageLink(HttpServletRequest request, int page) {
        StringBuilder link = new StringBuilder("?search=search&resultPage=" + page);
        for (String key : Arrays.asList("name", "cohortName", "cohortProgram", "cohortHead", "location", "startDate")) {
            String parameter = value(request, key);
            if (!parameter.isEmpty()) link.append("&").append(key).append("=")
                .append(URLEncoder.encode(parameter, StandardCharsets.UTF_8));
        }
        return link.toString();
    }

    private List<Map<String, Object>> cohortRows(HttpServletRequest request, ModelMap model,
            String filter, List<Object> arguments) {
        int page = pageNumber(request);
        List<Object> parameters = new ArrayList<>(arguments);
        parameters.add(COHORT_PAGE_SIZE + 1);
        parameters.add((long) page * COHORT_PAGE_SIZE);
        List<Map<String, Object>> rows = query("SELECT c.cohort_id AS cohortId,c.uuid,c.name,c.description,"
            + "c.startDate AS startDate,c.endDate AS endDate,c.voided,l.name AS clocation,"
            + "c.is_group_cohort AS groupCohort FROM cohort c LEFT JOIN location l ON l.location_id=c.location_id "
            + "WHERE c.voided=0" + filter + " ORDER BY c.cohort_id LIMIT ? OFFSET ?", parameters.toArray());
        boolean hasNext = rows.size() > COHORT_PAGE_SIZE;
        if (hasNext) rows.remove(COHORT_PAGE_SIZE);
        for (Map<String, Object> row : rows) row.put("cohortProgram", map("name", ""));
        model.addAttribute("resultPageNumber", (long) page + 1);
        model.addAttribute("resultOffset", (long) page * COHORT_PAGE_SIZE);
        if (page > 0) model.addAttribute("previousPageUrl", pageLink(request, page - 1));
        if (hasNext) model.addAttribute("nextPageUrl", pageLink(request, page + 1));
        return rows;
    }

    private List<Map<String, Object>> memberRows(String string, List<Object> list) {
        List<Map<String, Object>> list2 = this.query("SELECT cm.cohort_member_id AS cohortMemberId,cm.start_date AS startDate,cm.end_date AS endDate,c.name AS cohortName,p.person_id AS personId,p.uuid AS personUuid,p.gender,TIMESTAMPDIFF(YEAR,p.birthdate,CURRENT_DATE()) AS age,pn.given_name AS givenName,pn.family_name AS familyName,TRIM(CONCAT_WS(' ',NULLIF(pn.prefix,''),NULLIF(pn.given_name,''),NULLIF(pn.middle_name,''),NULLIF(pn.family_name_prefix,''),NULLIF(pn.family_name,''),NULLIF(pn.family_name2,''),NULLIF(pn.family_name_suffix,''),NULLIF(pn.degree,''))) AS fullName FROM cohort_member cm JOIN cohort c ON c.cohort_id=cm.cohort_id JOIN person p ON p.person_id=cm.patient_id JOIN person_name pn ON pn.person_name_id=(SELECT n.person_name_id FROM person_name n WHERE n.person_id=p.person_id AND n.voided=0 ORDER BY n.preferred DESC,n.person_name_id LIMIT 1)  LEFT JOIN location l ON l.location_id=c.location_id WHERE " + string + " ORDER BY cm.cohort_member_id LIMIT 1000", list.toArray());
        for (Map<String, Object> map : list2) {
            map.put("person", LegacyCohortController.map("personId", map.get("personId"), "uuid", map.get("personUuid"), "givenName", map.get("givenName"), "familyName", map.get("familyName"), "gender", map.get("gender"), "age", map.get("age"), "personName", LegacyCohortController.map("fullName", map.get("fullName"))));
            map.put("cohort", LegacyCohortController.map("name", map.get("cohortName")));
            map.put("role", "");
        }
        return list2;
    }

    private boolean blankOrEqual(String string, Object object) {
        return string.isEmpty() || string.equals(object == null ? "" : object.toString());
    }

    private boolean voided(Object object) {
        return Boolean.TRUE.equals(object) || object instanceof Number && ((Number)object).intValue() != 0;
    }

    private String dateString(Object object) {
        return object == null ? "" : new SimpleDateFormat("dd/MM/yyyy").format(object);
    }

    @RequestMapping(value={"/module/cohort/{page}.form"})
    public String page(@PathVariable(value="page") String string, HttpServletRequest httpServletRequest, ModelMap modelMap) {
        if (!Context.isAuthenticated()) {
            return "redirect:/login.htm";
        }
        Context.requirePrivilege("View Cohorts In Cohort Module");
        if (!"GET".equals(httpServletRequest.getMethod()) && !"POST".equals(httpServletRequest.getMethod())) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
        }
        if (string.equals("cohortDashboard")) {
            return this.dashboard(httpServletRequest, modelMap);
        }
        if (string.equals("cohortSearch")) {
            return this.cohortSearch(httpServletRequest, modelMap);
        }
        if (string.equals("patientSearch")) {
            return this.patientSearch(httpServletRequest, modelMap);
        }
        return this.management(string, httpServletRequest, modelMap);
    }

    private String dashboard(HttpServletRequest httpServletRequest, ModelMap modelMap) {
        CohortM cohortM = new CohortM();
        cohortM.setName(this.value(httpServletRequest, "name"));
        modelMap.addAttribute("cohortmodule", cohortM);
        if ("search".equals(this.value(httpServletRequest, "search"))) {
            ArrayList<Map<String, Object>> arrayList = new ArrayList<Map<String, Object>>();
            ArrayList<List<Map<String, Object>>> arrayList2 = new ArrayList<List<Map<String, Object>>>();
            String prefix = cohortM.getName().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            arrayList.addAll(cohortRows(httpServletRequest, modelMap, " AND c.name LIKE ? ESCAPE '!'", Arrays.asList(prefix)));
            for (Map<String, Object> row : arrayList) {
                arrayList2.add(this.memberRows("cm.cohort_id=?", Arrays.asList(row.get("cohortId"))));
            }
            modelMap.addAttribute("cohortExists", !arrayList.isEmpty());
            modelMap.addAttribute("cohortList", arrayList);
            modelMap.addAttribute("memberList", arrayList2);
            modelMap.addAttribute("htmlformId", 1);
            modelMap.addAttribute("resultLimit", 1000);
        }
        return "module/cohort/cohortDashboard";
    }

    private String cohortSearch(HttpServletRequest httpServletRequest, ModelMap modelMap) {
        if ("POST".equals(httpServletRequest.getMethod()) || "search".equals(this.value(httpServletRequest, "search"))) {
            boolean bl;
            ArrayList<Map<String, Object>> arrayList = new ArrayList<Map<String, Object>>();
            String string = this.value(httpServletRequest, "cohortHead");
            boolean bl2 = bl = string.isEmpty() || !this.memberRows("TRIM(CONCAT_WS(' ',NULLIF(pn.prefix,''),NULLIF(pn.given_name,''),NULLIF(pn.middle_name,''),NULLIF(pn.family_name_prefix,''),NULLIF(pn.family_name,''),NULLIF(pn.family_name2,''),NULLIF(pn.family_name_suffix,''),NULLIF(pn.degree,'')))=?", Arrays.asList(string)).isEmpty();
            if (this.value(httpServletRequest, "cohortProgram").isEmpty() && bl) {
                StringBuilder filter = new StringBuilder();
                List<Object> parameters = new ArrayList<>();
                for (String[] field : new String[][] {{"cohortName", "c.name"}, {"location", "l.name"}}) {
                    if (!value(httpServletRequest, field[0]).isEmpty()) {
                        filter.append(" AND ").append(field[1]).append("=?");
                        parameters.add(value(httpServletRequest, field[0]));
                    }
                }
                String startDate = value(httpServletRequest, "startDate");
                if (!startDate.isEmpty()) {
                    filter.append(" AND c.startDate >= STR_TO_DATE(?, '%d/%m/%Y') AND c.startDate < DATE_ADD(STR_TO_DATE(?, '%d/%m/%Y'), INTERVAL 1 DAY)");
                    parameters.add(startDate);
                    parameters.add(startDate);
                }
                arrayList.addAll(cohortRows(httpServletRequest, modelMap, filter.toString(), parameters));
            }
            modelMap.addAttribute("cohortsExist", !arrayList.isEmpty());
            modelMap.addAttribute("resultList", arrayList);
        }
        return "module/cohort/cohortSearch";
    }

    private String patientSearch(HttpServletRequest httpServletRequest, ModelMap modelMap) {
        if ("POST".equals(httpServletRequest.getMethod()) || "search".equals(this.value(httpServletRequest, "search"))) {
            int n;
            String string = this.value(httpServletRequest, "amount");
            if (string.isEmpty()) {
                string = "0 - 100";
            }
            if (!string.matches("[0-9]{1,3} - [0-9]{1,3}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Age range must be min - max");
            }
            String[] stringArray = string.split(" - ");
            int n2 = Integer.parseInt(stringArray[0]);
            if (n2 > (n = Integer.parseInt(stringArray[1]))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum age exceeds maximum age");
            }
            Object object = "p.gender=? AND TIMESTAMPDIFF(YEAR,p.birthdate,CURRENT_DATE()) BETWEEN ? AND ?";
            ArrayList<Object> arrayList = new ArrayList<Object>(Arrays.asList(this.value(httpServletRequest, "optionsRadios"), n2, n));
            if (!this.value(httpServletRequest, "patientName").isEmpty()) {
                object = (String)object + " AND TRIM(CONCAT_WS(' ',NULLIF(pn.prefix,''),NULLIF(pn.given_name,''),NULLIF(pn.middle_name,''),NULLIF(pn.family_name_prefix,''),NULLIF(pn.family_name,''),NULLIF(pn.family_name2,''),NULLIF(pn.family_name_suffix,''),NULLIF(pn.degree,'')))=?";
                arrayList.add((Serializable)((Object)this.value(httpServletRequest, "patientName")));
            }
            if (!this.value(httpServletRequest, "location").isEmpty()) {
                object = (String)object + " AND l.name=?";
                arrayList.add((Serializable)((Object)this.value(httpServletRequest, "location")));
            }
            List list = this.value(httpServletRequest, "cohortProgram").isEmpty() ? this.memberRows((String)object, arrayList) : Collections.emptyList();
            modelMap.addAttribute("personsExist", !list.isEmpty());
            modelMap.addAttribute("resultList", list);
            modelMap.addAttribute("resultLimit", 1000);
        }
        return "module/cohort/patientSearch";
    }

    private String uuid(String string, String string2, String string3) {
        if (string3.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> list = this.query("SELECT uuid FROM " + string + " WHERE uuid=? OR CAST(" + string2 + " AS CHAR)=?", string3, string3);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Record was not found");
        }
        return list.get(0).get("uuid").toString();
    }

    private CohortM cohort(String string) {
        return this.cohorts().getCohortMByUuid(this.uuid("cohort", "cohort_id", string));
    }

    private Date date(String string) throws Exception {
        if (string.isEmpty()) {
            return null;
        }
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy");
        simpleDateFormat.setLenient(false);
        return simpleDateFormat.parse(string);
    }

    private String required(HttpServletRequest httpServletRequest, String string) {
        String string2 = this.value(httpServletRequest, string);
        if (string2.isEmpty()) {
            throw new IllegalArgumentException(string + " is required");
        }
        return string2;
    }

    private Object property(Object object, String string) {
        try {
            return object.getClass().getMethod("get" + Character.toUpperCase(string.charAt(0)) + string.substring(1), new Class[0]).invoke(object, new Object[0]);
        }
        catch (Exception exception) {
            return "";
        }
    }

    private List<Map<String, Object>> options(Collection<?> collection) {
        ArrayList<Map<String, Object>> arrayList = new ArrayList<Map<String, Object>>();
        for (Object obj : collection) {
            arrayList.add(LegacyCohortController.map("id", this.property(obj, "uuid"), "name", this.property(obj, "name")));
        }
        return arrayList;
    }

    private Map<String, Object> field(String string, String string2, Object object, String string3, Object object2) {
        return LegacyCohortController.map("name", string, "label", string2, "value", object == null ? "" : object, "type", string3, "options", object2);
    }

    private void dates(CohortM cohortM, HttpServletRequest httpServletRequest) throws Exception {
        cohortM.setStartDate(this.date(this.value(httpServletRequest, "startDate")));
        cohortM.setEndDate(this.date(this.value(httpServletRequest, "endDate")));
        if (cohortM.getStartDate() != null && cohortM.getEndDate() != null && cohortM.getEndDate().before(cohortM.getStartDate())) {
            throw new IllegalArgumentException("Start date should be less than End date");
        }
    }

    private String management(String string, HttpServletRequest httpServletRequest, ModelMap modelMap) {
        Object object2;
        Patient object3;
        List<Map<String, Object>> list;
        Serializable serializable;
        String string2;
        HashSet<String> hashSet = new HashSet<String>(Arrays.asList("addCohort", "editCohort", "groupcohort"));
        if (hashSet.contains(string)) {
            string2 = "cohort";
        } else if (string.equals("cPatients")) {
            string2 = "member";
        } else if (string.contains("MemberAttributeType")) {
            string2 = "memberType";
        } else if (string.contains("AttributesType")) {
            string2 = "attributeType";
        } else if (string.contains("MemberAttribute")) {
            string2 = "memberAttribute";
        } else if (string.contains("CohortAttributes")) {
            string2 = "attribute";
        } else if (string.contains("CohortType")) {
            string2 = "type";
        } else {
            if (Arrays.asList("manageCohortProgram", "manageCohortRole", "htmlFormEntry", "configurecohortmetadata").contains(string)) {
                modelMap.addAttribute("pageTitle", "Cohort metadata");
                modelMap.addAttribute("notice", "Cohort 3.x does not provide the former cohort program, member role or cohort-level encounter features. Patient encounters remain available from each patient's record.");
                return "module/cohort/legacyManagement";
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String string3 = this.value(httpServletRequest, "id");
        String string4 = this.value(httpServletRequest, "parent");
        if (string2.equals("cohort") && string3.isEmpty()) {
            string3 = this.value(httpServletRequest, "cid");
        }
        if (string2.equals("member") && string4.isEmpty()) {
            string4 = this.value(httpServletRequest, "cpid");
        }
        if (string2.equals("attribute") && string4.isEmpty()) {
            string4 = this.value(httpServletRequest, "ca");
        }
        if (string2.equals("memberAttribute") && string4.isEmpty()) {
            string4 = this.value(httpServletRequest, "cma");
        }
        modelMap.addAttribute("pageTitle", string2.equals("cohort") ? (string.equals("editCohort") ? "Edit Cohort" : "Create Cohort") : (string2.equals("member") ? "Cohort Members" : (string2.equals("type") ? "Cohort Types" : (string2.equals("attributeType") ? "Cohort Attribute Types" : (string2.equals("memberType") ? "Cohort Member Attribute Types" : (string2.equals("attribute") ? "Cohort Attributes" : "Cohort Member Attributes"))))));
        if ("POST".equals(httpServletRequest.getMethod())) {
            Context.requirePrivilege("Edit Cohorts in Cohort Module");
            try {
                if (string2.equals("cohort")) {
                    CohortM cohortM = string3.isEmpty() ? new CohortM() : this.cohort(string3);
                    cohortM.setName(this.required(httpServletRequest, "name"));
                    cohortM.setDescription(this.value(httpServletRequest, "description"));
                    this.dates(cohortM, httpServletRequest);
                    cohortM.setCohortType(this.types().getCohortTypeByUuid(this.required(httpServletRequest, "cohortType")));
                    cohortM.setLocation(this.value(httpServletRequest, "location").isEmpty() ? null : Context.getLocationService().getLocationByUuid(this.value(httpServletRequest, "location")));
                    cohortM.setGroupCohort(string.equals("groupcohort") || "true".equals(this.value(httpServletRequest, "groupCohort")));
                    this.cohorts().saveCohortM(cohortM);
                    return "redirect:cohortDashboard.form?search=search&name=" + URLEncoder.encode(cohortM.getName(), StandardCharsets.UTF_8);
                }
                if (string2.equals("type")) {
                    serializable = string3.isEmpty() ? new CohortType() : this.types().getCohortTypeByUuid(this.uuid("cohort_type", "cohort_type_id", string3));
                    ((CohortType)serializable).setName(this.required(httpServletRequest, "name"));
                    ((CohortType)serializable).setDescription(this.value(httpServletRequest, "description"));
                    this.types().saveCohortType((CohortType)serializable);
                } else if (string2.equals("attributeType")) {
                    serializable = string3.isEmpty() ? new CohortAttributeType() : this.cohorts().getCohortAttributeTypeByUuid(string3);
                    ((BaseOpenmrsMetadata)serializable).setName(this.required(httpServletRequest, "name"));
                    ((BaseOpenmrsMetadata)serializable).setDescription(this.value(httpServletRequest, "description"));
                    ((BaseAttributeType)serializable).setDatatypeClassname(this.required(httpServletRequest, "datatype"));
                    ((BaseAttributeType)serializable).setMinOccurs(0);
                    this.cohorts().saveCohortAttributeType((CohortAttributeType)serializable);
                } else if (string2.equals("memberType")) {
                    serializable = string3.isEmpty() ? new CohortMemberAttributeType() : this.members().getCohortMemberAttributeTypeByUuid(string3);
                    ((BaseOpenmrsMetadata)serializable).setName(this.required(httpServletRequest, "name"));
                    ((BaseOpenmrsMetadata)serializable).setDescription(this.value(httpServletRequest, "description"));
                    ((BaseAttributeType)serializable).setDatatypeClassname(this.required(httpServletRequest, "datatype"));
                    ((BaseAttributeType)serializable).setMinOccurs(0);
                    this.members().saveCohortMemberAttributeType((CohortMemberAttributeType)serializable);
                } else if (string2.equals("member")) {
                    serializable = this.cohort(string4);
                    String patientValue = this.required(httpServletRequest, "patient");
                    object3 = Context.getPatientService().getPatientByUuid(patientValue);
                    if (object3 == null && patientValue.matches("[0-9]+")) {
                        object3 = Context.getPatientService().getPatient(Integer.valueOf(patientValue));
                    }
                    if (object3 == null) {
                        List<Patient> matches = Context.getPatientService().getPatients(patientValue);
                        if (matches.size() == 1) object3 = matches.get(0);
                    }
                    if (object3 == null) {
                        throw new IllegalArgumentException("Choose one patient by identifier or UUID");
                    }
                    for (CohortMember object4 : this.members().findCohortMembersByCohortUuid(((BaseOpenmrsObject)serializable).getUuid())) {
                        if (object4.getVoided().booleanValue() || !object4.getPatient().getUuid().equals(object3.getUuid())) continue;
                        throw new IllegalArgumentException("A cohort cannot have duplicate patients");
                    }
                    object2 = new CohortMember(object3);
                    ((CohortMember)object2).setCohort((CohortM)serializable);
                    ((CohortMember)object2).setStartDate(this.date(this.value(httpServletRequest, "startDate")));
                    ((CohortMember)object2).setEndDate(this.date(this.value(httpServletRequest, "endDate")));
                    if (((CohortMember)object2).getStartDate() != null && ((CohortMember)object2).getEndDate() != null && ((CohortMember)object2).getEndDate().before(((CohortMember)object2).getStartDate())) {
                        throw new IllegalArgumentException("End date must be after Start date");
                    }
                    this.members().saveCohortMember((CohortMember)object2);
                } else if (string2.equals("attribute")) {
                    serializable = new CohortAttribute();
                    ((CohortAttribute)serializable).setCohort(this.cohort(string4));
                    ((BaseAttribute)serializable).setAttributeType(this.cohorts().getCohortAttributeTypeByUuid(this.required(httpServletRequest, "attributeType")));
                    ((BaseAttribute)serializable).setValueReferenceInternal(this.required(httpServletRequest, "value"));
                    this.cohorts().saveCohortAttribute((CohortAttribute)serializable);
                } else if (string2.equals("memberAttribute")) {
                    serializable = new CohortMemberAttribute();
                    ((CohortMemberAttribute)serializable).setCohortMember(this.members().getCohortMemberByUuid(this.uuid("cohort_member", "cohort_member_id", string4)));
                    ((BaseAttribute)serializable).setAttributeType(this.members().getCohortMemberAttributeTypeByUuid(this.required(httpServletRequest, "attributeType")));
                    ((BaseAttribute)serializable).setValueReferenceInternal(this.required(httpServletRequest, "value"));
                    this.members().saveCohortMemberAttribute((CohortMemberAttribute)serializable);
                }
                modelMap.addAttribute("saved", true);
            }
            catch (Exception exception) {
                modelMap.addAttribute("error", exception.getMessage());
            }
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        list = new ArrayList();
        Object selectedRecord = null;
        try {
            if (string2.equals("cohort")) {
                object2 = string3.isEmpty() ? new CohortM() : this.cohort(string3);
                fields.add(this.field("name", "Name", ((CohortM)object2).getName(), "text", null));
                fields.add(this.field("description", "Description", ((CohortM)object2).getDescription(), "text", null));
                fields.add(this.field("cohortType", "Cohort type", ((CohortM)object2).getCohortType() == null ? "" : ((CohortM)object2).getCohortType().getUuid(), "select", this.options(this.types().findAllCohortTypes())));
                fields.add(this.field("location", "Location", ((CohortM)object2).getLocation() == null ? "" : ((CohortM)object2).getLocation().getUuid(), "select", this.options(Context.getLocationService().getAllLocations())));
                fields.add(this.field("startDate", "Start date (dd/mm/yyyy)", this.dateString(((CohortM)object2).getStartDate()), "text", null));
                fields.add(this.field("endDate", "End date (dd/mm/yyyy)", this.dateString(((CohortM)object2).getEndDate()), "text", null));
                fields.add(this.field("groupCohort", "Group cohort", ((CohortM)object2).getGroupCohort(), "checkbox", null));
            } else if (Arrays.asList("type", "attributeType", "memberType").contains(string2)) {
                object2 = string2.equals("type") ? this.types().findAllCohortTypes() : (string2.equals("attributeType") ? this.cohorts().findAllCohortAttributeTypes() : this.members().findAllCohortMemberAttributeTypes());
                Iterator iterator = ((Collection<?>) object2).iterator();
                while (iterator.hasNext()) {
                    Object e = iterator.next();
                    String string5 = this.property(e, "uuid").toString();
                    list.add(LegacyCohortController.map("id", string5, "name", this.property(e, "name"), "description", this.property(e, "description"), "edit", string + ".form?id=" + string5));
                    if (!string5.equals(string3)) continue;
                    selectedRecord = e;
                }
                fields.add(this.field("name", "Name", selectedRecord == null ? "" : this.property(selectedRecord, "name"), "text", null));
                fields.add(this.field("description", "Description", selectedRecord == null ? "" : this.property(selectedRecord, "description"), "text", null));
                if (!string2.equals("type")) {
                    ArrayList<Map<String, Object>> arrayList = new ArrayList<Map<String, Object>>();
                    for (String string6 : Arrays.asList("FreeText", "Boolean", "Date", "Float", "RegexValidatedText")) {
                        arrayList.add(LegacyCohortController.map("id", "org.openmrs.customdatatype.datatype." + string6 + "Datatype", "name", string6));
                    }
                    fields.add(this.field("datatype", "Format", selectedRecord == null ? "" : this.property(selectedRecord, "datatypeClassname"), "select", arrayList));
                }
            } else if (string2.equals("member")) {
                object2 = this.cohort(string4);
                modelMap.addAttribute("parentLabel", ((CohortM)object2).getName());
                list = this.memberRows("cm.cohort_id=?", Arrays.asList(((CohortM)object2).getId()));
                for (Map<String, Object> map : list) {
                    map.put("name", map.get("fullName"));
                    map.put("description", this.dateString(map.get("startDate")));
                    map.put("edit", "addCohortMemberAttributes.form?cma=" + String.valueOf(map.get("cohortMemberId")));
                }
                fields.add(this.field("patient", "Patient identifier or UUID", "", "text", null));
                fields.add(this.field("startDate", "Start date (dd/mm/yyyy)", "", "text", null));
                fields.add(this.field("endDate", "End date (dd/mm/yyyy)", "", "text", null));
            } else {
                boolean bl = string2.equals("memberAttribute");
                Collection<? extends BaseAttributeType<?>> collection = bl ? this.members().findAllCohortMemberAttributeTypes() : this.cohorts().findAllCohortAttributeTypes();
                fields.add(this.field("attributeType", "Attribute type", "", "select", this.options(collection)));
                fields.add(this.field("value", "Value", "", "text", null));
                if (!string4.isEmpty()) {
                    if (bl) {
                        String string7 = this.uuid("cohort_member", "cohort_member_id", string4);
                        list = this.query("SELECT t.name,a.value_reference AS description FROM cohort_member_attribute a JOIN cohort_member_attribute_type t ON t.cohort_member_attribute_type_id=a.cohort_member_attribute_type_id JOIN cohort_member m ON m.cohort_member_id=a.cohort_member_id WHERE m.uuid=? AND a.voided=0 ORDER BY a.cohort_member_attribute_id LIMIT 1000", string7);
                    } else {
                        Collection<CohortAttribute> collection2 = this.cohorts().findCohortAttributesByCohortUuid(this.cohort(string4).getUuid());
                        for (CohortAttribute cohortAttribute : collection2) {
                            list.add(LegacyCohortController.map("name", this.property(this.property(cohortAttribute, "attributeType"), "name"), "description", this.property(cohortAttribute, "valueReference")));
                        }
                    }
                }
            }
        }
        catch (Exception exception) {
            modelMap.addAttribute("error", exception.getMessage());
        }
        if ("POST".equals(httpServletRequest.getMethod()) && modelMap.containsAttribute("error")) {
            Iterator iterator = fields.iterator();
            while (iterator.hasNext()) {
                Map map = (Map)iterator.next();
                map.put("value", this.value(httpServletRequest, map.get("name").toString()));
            }
        }
        modelMap.addAttribute("fields", fields);
        modelMap.addAttribute("rows", list);
        modelMap.addAttribute("recordId", string3);
        modelMap.addAttribute("parentId", string4);
        return "module/cohort/legacyManagement";
    }
}
