package com.dfs.corporate.service;

import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pakistan-oriented UBP catalog + mock bill fetch (portal demo only).
 */
@Component
public class UbpMockCatalog {

    public record Biller(String code, String name) {}

    public record Category(String code, String label, String consumerLabel, List<Biller> companies) {}

    private static final List<Category> CATEGORIES = List.of(
            new Category("ELECTRICITY", "Electricity", "Consumer number", List.of(
                    b("LESCO", "LESCO"),
                    b("KE", "K-Electric"),
                    b("IESCO", "IESCO"),
                    b("FESCO", "FESCO"),
                    b("MEPCO", "MEPCO"),
                    b("GEPCO", "GEPCO"),
                    b("PESCO", "PESCO"),
                    b("HESCO", "HESCO"),
                    b("QESCO", "QESCO"),
                    b("SEPCO", "SEPCO"),
                    b("TESCO", "TESCO")
            )),
            new Category("GAS", "Gas", "Consumer number", List.of(
                    b("SNGPL", "SNGPL"),
                    b("SSGC", "SSGC")
            )),
            new Category("WATER", "Water", "Consumer / connection number", List.of(
                    b("WASA_LHR", "WASA Lahore"),
                    b("WASA_ISB", "WASA Islamabad / RWP"),
                    b("KWSB", "KW&SB Karachi"),
                    b("WASA_FSD", "WASA Faisalabad")
            )),
            new Category("INTERNET", "Internet / Broadband", "Account / customer ID", List.of(
                    b("PTCL", "PTCL"),
                    b("NAYATEL", "Nayatel"),
                    b("STORMFIBER", "StormFiber"),
                    b("TRANSWORLD", "Transworld"),
                    b("OPTIX", "Optix")
            )),
            new Category("MOBILE", "Mobile (prepaid / postpaid)", "Mobile number (03XXXXXXXXX)", List.of(
                    b("JAZZ", "Jazz"),
                    b("TELENOR", "Telenor"),
                    b("ZONG", "Zong"),
                    b("UFONE", "Ufone")
            )),
            new Category("LANDLINE", "Landline / Fixed line", "PTCL / landline number", List.of(
                    b("PTCL_LL", "PTCL Landline")
            )),
            new Category("TV", "TV / Cable / DTH", "Subscriber / smart card number", List.of(
                    b("PTCL_TV", "PTCL Smart TV"),
                    b("CABLE_LOCAL", "Local cable (mock)")
            )),
            new Category("TICKETS", "Tickets / Transport", "PNR / booking / ticket reference", List.of(
                    b("PAK_RAILWAYS", "Pakistan Railways"),
                    b("LOCAL_BUS", "Local bus / metro (mock)")
            )),
            new Category("EDUCATION", "Education / Fees", "Challan / voucher number", List.of(
                    b("SCHOOL_FEE", "School / college fee (mock)"),
                    b("UNI_FEE", "University challan (mock)")
            )),
            new Category("GOVERNMENT", "Government / Taxes", "PSID / challan / reference", List.of(
                    b("FBR", "FBR (mock)"),
                    b("PROVINCIAL", "Provincial tax (mock)")
            ))
    );

    private static Biller b(String code, String name) {
        return new Biller(code, name);
    }

    public List<Map<String, Object>> catalogPayload() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Category c : CATEGORIES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.code());
            m.put("label", c.label());
            m.put("consumerLabel", c.consumerLabel());
            List<Map<String, String>> companies = new ArrayList<>();
            for (Biller biller : c.companies()) {
                companies.add(Map.of("code", biller.code(), "name", biller.name()));
            }
            m.put("companies", companies);
            out.add(m);
        }
        return out;
    }

    public Category requireCategory(String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UBP category is required");
        }
        String key = code.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.stream()
                .filter(c -> c.code().equals(key))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown UBP category: " + code));
    }

    public Biller requireCompany(Category category, String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UBP company / biller is required");
        }
        String key = companyCode.trim().toUpperCase(Locale.ROOT);
        return category.companies().stream()
                .filter(b -> b.code().equals(key))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Unknown company for " + category.label() + ": " + companyCode));
    }

    public Map<String, Object> fetchBill(String categoryCode, String companyCode, String consumerNumber) {
        Category category = requireCategory(categoryCode);
        Biller company = requireCompany(category, companyCode);
        if (consumerNumber == null || consumerNumber.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, category.consumerLabel() + " is required");
        }
        String consumer = consumerNumber.trim();
        if ("MOBILE".equals(category.code())) {
            String digits = consumer.replaceAll("\\D", "");
            if (digits.length() < 10) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid mobile number (03XXXXXXXXX)");
            }
        }

        YearMonth month = YearMonth.now().minusMonths(1);
        String billingMonth = month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
        LocalDate due = LocalDate.now().plusDays(7 + ThreadLocalRandom.current().nextInt(0, 10));
        BigDecimal amount = mockAmount(category.code(), consumer);
        String customerName = mockCustomerName(consumer);

        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("mock", true);
        bill.put("category", category.code());
        bill.put("categoryLabel", category.label());
        bill.put("companyCode", company.code());
        bill.put("companyName", company.name());
        bill.put("consumerNumber", consumer);
        bill.put("consumerLabel", category.consumerLabel());
        bill.put("customerName", customerName);
        bill.put("billingMonth", billingMonth);
        bill.put("dueDate", due.toString());
        bill.put("dueAmount", amount);
        bill.put("currency", "PKR");
        bill.put("status", "UNPAID");
        bill.put("message", "Mock bill fetched — not a live disco / 1LINK inquiry");
        return bill;
    }

    private BigDecimal mockAmount(String category, String consumer) {
        int seed = Math.abs(Objects.hash(category, consumer)) % 9000;
        double base = switch (category) {
            case "ELECTRICITY" -> 2500 + seed;
            case "GAS" -> 1800 + (seed % 4000);
            case "WATER" -> 800 + (seed % 2000);
            case "INTERNET", "LANDLINE" -> 2000 + (seed % 5000);
            case "MOBILE" -> 500 + (seed % 3000);
            case "TV" -> 1200 + (seed % 2500);
            case "TICKETS" -> 1500 + (seed % 8000);
            case "EDUCATION" -> 5000 + (seed % 20000);
            case "GOVERNMENT" -> 1000 + (seed % 15000);
            default -> 1000 + seed;
        };
        return BigDecimal.valueOf(base).setScale(2, RoundingMode.HALF_UP);
    }

    private String mockCustomerName(String consumer) {
        String[] first = {"Ahmed", "Fatima", "Hassan", "Ayesha", "Ali", "Sana", "Bilal", "Maryam"};
        String[] last = {"Khan", "Ahmed", "Malik", "Hussain", "Raza", "Sheikh", "Iqbal", "Butt"};
        int h = Math.abs(consumer.hashCode());
        return first[h % first.length] + " " + last[(h / first.length) % last.length];
    }
}
