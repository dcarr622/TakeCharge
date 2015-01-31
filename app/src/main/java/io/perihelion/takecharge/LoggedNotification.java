package io.perihelion.takecharge;

public class LoggedNotification {

    private String appName;
    private String tickerText;
    private int id;

    public LoggedNotification(String appName, String tickerText, int id) {
        this.appName = appName;
        this.tickerText = tickerText;
        this.id = id;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getTickerText() {
        return tickerText;
    }

    public void setTickerText(String tickerText) {
        this.tickerText = tickerText;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
