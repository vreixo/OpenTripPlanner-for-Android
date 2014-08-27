package edu.usf.cutr.opentripplanner.android.util;

/**
 * Created by foucelhas on 15/07/14.
 */
public class TripInfo {

    boolean realtime;

    String tripId;

    double destinationLatitude;

    double destinationLongitude;

    CharSequence snippet;

    int delayInSeconds;

    public TripInfo(boolean realtime, String tripId, double destinationLatitude,
                    double destinationLongitude, CharSequence snippet, int delayInSeconds) {
        this.realtime = realtime;
        this.delayInSeconds = delayInSeconds;
        this.tripId = tripId;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.snippet = snippet;
    }

    public boolean isRealtime() {
        return realtime;
    }

    public void setRealtime(boolean realtime) {
        this.realtime = realtime;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public int getDelayInSeconds() {
        return delayInSeconds;
    }

    public void setDelayInSeconds(int delayInSeconds) {
        this.delayInSeconds = delayInSeconds;
    }

    public CharSequence getSnippet() {
        return snippet;
    }

    public void setSnippet(CharSequence snippet) {
        this.snippet = snippet;
    }

    public double getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(double destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    public double getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(double destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }
}
