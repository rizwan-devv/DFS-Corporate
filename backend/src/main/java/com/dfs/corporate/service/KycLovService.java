package com.dfs.corporate.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static Pakistan province/city LOVs for KYC app (until DFS backend LOV APIs are wired).
 */
@Service
public class KycLovService {

    public Map<String, Object> lovs() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provinces", provinces());
        out.put("cities", cities());
        return out;
    }

    public List<Map<String, String>> provinces() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(item("1", "Punjab", null));
        list.add(item("2", "Sindh", null));
        list.add(item("3", "Khyber Pakhtunkhwa", null));
        list.add(item("4", "Balochistan", null));
        list.add(item("5", "Islamabad Capital Territory", null));
        list.add(item("6", "Gilgit-Baltistan", null));
        list.add(item("7", "Azad Jammu & Kashmir", null));
        return list;
    }

    public List<Map<String, String>> cities() {
        List<Map<String, String>> list = new ArrayList<>();
        // Punjab
        list.add(item("101", "Lahore", "1"));
        list.add(item("102", "Faisalabad", "1"));
        list.add(item("103", "Rawalpindi", "1"));
        list.add(item("104", "Multan", "1"));
        list.add(item("105", "Gujranwala", "1"));
        list.add(item("106", "Sialkot", "1"));
        list.add(item("107", "Bahawalpur", "1"));
        // Sindh
        list.add(item("201", "Karachi", "2"));
        list.add(item("202", "Hyderabad", "2"));
        list.add(item("203", "Sukkur", "2"));
        list.add(item("204", "Larkana", "2"));
        // KPK
        list.add(item("301", "Peshawar", "3"));
        list.add(item("302", "Abbottabad", "3"));
        list.add(item("303", "Mardan", "3"));
        // Balochistan
        list.add(item("401", "Quetta", "4"));
        list.add(item("402", "Gwadar", "4"));
        // ICT
        list.add(item("501", "Islamabad", "5"));
        // GB
        list.add(item("601", "Gilgit", "6"));
        // AJK
        list.add(item("701", "Muzaffarabad", "7"));
        return list;
    }

    private static Map<String, String> item(String id, String name, String provinceId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        if (provinceId != null) {
            m.put("provinceId", provinceId);
        }
        return m;
    }
}
