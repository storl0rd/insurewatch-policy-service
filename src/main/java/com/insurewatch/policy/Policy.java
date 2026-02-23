package com.insurewatch.policy;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "policies")
public class Policy {
    @Id
    private String id;
    private String customerId;
    private String policyNumber;
    private String policyType; // health, auto, property, life
    private String status;     // active, inactive, suspended
    private double premiumAmount;
    private String startDate;
    private String endDate;
    private List<Coverage> coverages;

    public static class Coverage {
        private String type;
        private double limit;
        private double deductible;
        private double used;

        public Coverage() {}
        public Coverage(String type, double limit, double deductible, double used) {
            this.type = type; this.limit = limit;
            this.deductible = deductible; this.used = used;
        }
        public String getType() { return type; }
        public double getLimit() { return limit; }
        public double getDeductible() { return deductible; }
        public double getUsed() { return used; }
        public double getAvailable() { return limit - used; }
        public void setType(String t) { this.type = t; }
        public void setLimit(double l) { this.limit = l; }
        public void setDeductible(double d) { this.deductible = d; }
        public void setUsed(double u) { this.used = u; }
    }

    public Policy() {}
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String c) { this.customerId = c; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String p) { this.policyNumber = p; }
    public String getPolicyType() { return policyType; }
    public void setPolicyType(String t) { this.policyType = t; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public double getPremiumAmount() { return premiumAmount; }
    public void setPremiumAmount(double p) { this.premiumAmount = p; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String s) { this.startDate = s; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String e) { this.endDate = e; }
    public List<Coverage> getCoverages() { return coverages; }
    public void setCoverages(List<Coverage> c) { this.coverages = c; }
    public double getCoverageLimit() {
        return coverages != null ? coverages.stream().mapToDouble(Coverage::getLimit).sum() : 0;
    }
}
