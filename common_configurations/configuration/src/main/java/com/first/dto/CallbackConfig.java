package com.first.dto;

public class CallbackConfig {
    private String event;
    private String type;
    private String url;
    private String template;
    private String recipient;

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
}