/*
 * Copyright 2012 University of South Florida
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package edu.usf.cutr.opentripplanner.android.util;

import org.opentripplanner.api.model.AgencyAndId;
import org.opentripplanner.api.model.RelativeDirection;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.patch.Alerts;
import org.opentripplanner.api.model.AbsoluteDirection;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.Place;
import org.opentripplanner.api.model.WalkStep;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.text.SpannableString;

import com.google.android.gms.maps.model.Marker;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import edu.usf.cutr.opentripplanner.android.OTPApp;
import edu.usf.cutr.opentripplanner.android.R;
import edu.usf.cutr.opentripplanner.android.listeners.OTPGeocodingListener;
import edu.usf.cutr.opentripplanner.android.model.Direction;
import edu.usf.cutr.opentripplanner.android.tasks.OTPGeocoding;

/**
 * Generates a set of step-by-step directions that can be shown to the user from a list of trip
 * legs
 *
 * @author Khoa Tran
 */

public class DirectionsGenerator implements OTPGeocodingListener{

    private List<Leg> legs = new ArrayList<Leg>();

    private ArrayList<Direction> directions = new ArrayList<Direction>();

    private double totalDistance = 0;

    private double totalTimeTraveled = 0;

    private Context applicationContext;

    private Map<Marker, TripInfo> modeMarkers;

    public DirectionsGenerator(Map<Marker, TripInfo> modeMarkers, Context applicationContext) {
        this.applicationContext = applicationContext;
        this.modeMarkers = modeMarkers;
    }

    public DirectionsGenerator(List<Leg> legs, Context applicationContext) {
        this.legs.addAll(legs);
        this.applicationContext = applicationContext;

        convertToDirectionList();
    }

    /**
     * @return the directions
     */
    public ArrayList<Direction> getDirections() {
        return directions;
    }

    /**
     * @param directions the directions to set
     */
    public void setDirections(ArrayList<Direction> directions) {
        this.directions = directions;
    }

    public void addDirection(Direction dir) {
        if (directions == null) {
            directions = new ArrayList<Direction>();
        }

        directions.add(dir);
    }

    private void convertToDirectionList() {
        int index = 0;
        for (Leg leg : legs) {
            index++;
            setTotalDistance(getTotalDistance() + leg.distance);

            TraverseMode traverseMode = TraverseMode.valueOf((String) leg.mode);
            if (traverseMode.isOnStreetNonTransit()) {
                Direction dir = generateNonTransitDirections(leg);
                if (dir == null) {
                    continue;
                }
                dir.setDirectionIndex(index);
                addDirection(dir);
            } else {
                ArrayList<Direction> directions = generateTransitDirections(leg);
                if (directions == null) {
                    continue;
                }

                if (directions.get(0) != null) {
                    directions.get(0).setDirectionIndex(index);
                    addDirection(directions.get(0));
                }

                if (directions.get(1) != null) {
                    if (directions.get(0) != null) {
                        index++;
                    }
                    directions.get(1).setDirectionIndex(index);
                    addDirection(directions.get(1));
                }
            }

            //			directionText+=leg.mode+"\n";
        }
    }

    private Direction generateNonTransitDirections(Leg leg) {
        Direction direction = new Direction();

        //http://opentripplanner.usf.edu/opentripplanner-api-webapp/ws/plan?optimize=QUICK&time=09:24pm&arriveBy=false&wheelchair=false&maxWalkDistance=7600.0&fromPlace=28.033389%2C+-82.521034&toPlace=28.064709%2C+-82.471618&date=03/07/12&mode=WALK,TRAM,SUBWAY,RAIL,BUS,FERRY,CABLE_CAR,GONDOLA,FUNICULAR,TRANSIT,TRAINISH,BUSISH

        // Get appropriate action and icon
        String action = applicationContext.getResources().getString(R.string.step_by_step_non_transit_mode_walk_action);
        TraverseMode mode = TraverseMode.valueOf((String) leg.mode);
        int icon = getModeIcon(new TraverseModeSet(mode));
        if (mode.compareTo(TraverseMode.BICYCLE) == 0) {
            action = applicationContext.getResources().getString(R.string.step_by_step_non_transit_mode_bicycle_action);
        } else if (mode.compareTo(TraverseMode.CAR) == 0) {
            action = applicationContext.getResources().getString(R.string.step_by_step_non_transit_mode_car_action);
        }

        direction.setIcon(icon);

        //		Main direction
        Place fromPlace = leg.from;
        Place toPlace = leg.to;
        String mainDirectionText = action;
        mainDirectionText += fromPlace.name == null ? ""
                : " " + applicationContext.getResources().getString(R.string.step_by_step_non_transit_from)
                        + " " + getLocalizedStreetName(null, fromPlace);
        mainDirectionText += toPlace.name == null ? ""
                : " " + applicationContext.getResources().getString(R.string.step_by_step_non_transit_to) + " "
                        + getLocalizedStreetName(null, toPlace);
        mainDirectionText += toPlace.stopId == null ? ""
                : " (" + toPlace.stopId.getAgencyId() + " " + toPlace.stopId.getId() + ")";
        mainDirectionText += "\n[" + ConversionUtils
                .getFormattedDistance(totalDistance, applicationContext)
                + " ]";
        direction.setDirectionText(mainDirectionText);

        direction.setOriginLatitude(fromPlace.getLat());
        direction.setOriginLongitude(fromPlace.getLon());
        direction.setDestinationLatitude(toPlace.getLat());
        direction.setDestinationLongitude(toPlace.getLon());

        //Sub-direction
        List<WalkStep> walkSteps = leg.getSteps();

        if (walkSteps == null) {
            return direction;
        }

        ArrayList<Direction> subDirections = new ArrayList<Direction>(walkSteps.size());

        for (WalkStep step : walkSteps) {
            int subdirection_icon = R.drawable.clear;
            Direction dir = new Direction();
            String subDirectionText = "";

            double distance = step.distance;

            RelativeDirection relativeDir = step.relativeDirection;
            String relativeDirString = getLocalizedRelativeDir(relativeDir,
                    applicationContext.getResources());
            AbsoluteDirection absoluteDir = step.absoluteDirection;
            String absoluteDirString = getLocalizedAbsoluteDir(absoluteDir,
                    applicationContext.getResources());
            String exit = step.exit;
            boolean isStayOn = (step.stayOn == null ? false : step.stayOn);
            boolean isBogusName = (step.bogusName == null ? false : step.bogusName);
            double lon = step.lon;
            double lat = step.lat;
            String streetConnector = applicationContext.getResources()
                    .getString(R.string.step_by_step_non_transit_connector_street_name);
            //Elevation[] elevation = step.getElevation();  //Removed elevation for now, since we're not doing anything with it and it causes version issues between OTP server APIs v0.9.1-SNAPSHOT and v0.9.2-SNAPSHOT
            List<Alerts> alert = step.alerts;

            // Walk East
            if (relativeDir == null) {
                subDirectionText += action + " " + applicationContext.getResources()
                        .getString(R.string.step_by_step_non_transit_heading) + " ";
                subDirectionText += absoluteDirString + " ";
            }
            // (Turn left)/(Continue)
            else {
                RelativeDirection rDir = RelativeDirection.valueOf(relativeDir.name());

                subdirection_icon = getRelativeDirectionIcon(rDir);

                // Do not need TURN Continue
                if (rDir.compareTo(RelativeDirection.RIGHT) == 0 ||
                        rDir.compareTo(RelativeDirection.LEFT) == 0) {
                    subDirectionText += applicationContext.getResources()
                            .getString(R.string.step_by_step_non_transit_turn) + " ";
                }

                subDirectionText += relativeDirString + " ";

                if (rDir.compareTo(RelativeDirection.CIRCLE_CLOCKWISE) == 0
                        || rDir.compareTo(RelativeDirection.CIRCLE_COUNTERCLOCKWISE) == 0) {
                    if (step.exit != null) {
                        try {
                            String ordinal = getOrdinal(Integer.parseInt(step.exit),
                                    applicationContext.getResources());
                            if (ordinal != null) {
                                subDirectionText += ordinal + " ";
                            } else {
                                subDirectionText += applicationContext.getResources()
                                        .getString(R.string.step_by_step_non_transit_roundabout_number) + " " + ordinal
                                        + " ";
                            }
                        } catch (NumberFormatException e) {
                            //If is not a step_by_step_non_transit_roundabout_number and is not null is better to try to display it
                            subDirectionText += step.exit + " ";
                        }
                        subDirectionText += applicationContext.getResources()
                                .getString(R.string.step_by_step_non_transit_roundabout_exit) + " ";
                        streetConnector = applicationContext.getResources()
                                .getString(R.string.step_by_step_non_transit_connector_street_name_roundabout);
                    }
                }
            }

            subDirectionText += streetConnector + " "
                    + getLocalizedStreetName(step, null) + " ";

            subDirectionText += "\n[" + ConversionUtils
                    .getFormattedDistance(distance, applicationContext) + " ]";

            dir.setDirectionText(subDirectionText);

            dir.setIcon(subdirection_icon);

            dir.setOriginLatitude(step.getLat());
            dir.setOriginLongitude(step.getLon());
            dir.setDestinationLatitude(step.getLat());
            dir.setDestinationLongitude(step.getLon());

            // Add new sub-direction
            subDirections.add(dir);
        }

        direction.setSubDirections(subDirections);

        return direction;
    }

    private static String getOrdinal(int number, Resources resources) {
        switch (number) {
            case 1:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_first);
            case 2:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_second);
            case 3:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_third);
            case 4:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_fourth);
            case 5:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_fifth);
            case 6:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_sixth);
            case 7:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_seventh);
            case 8:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_eighth);
            case 9:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_ninth);
            case 10:
                return resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_tenth);
            default:
                return null;
        }

    }

    // Dirty fix to avoid the presence of names for unnamed streets (as road, track, etc.) for other languages than English
    public String getLocalizedStreetName(WalkStep walkStep, Place place) {
        if (place != null || walkStep != null) {
            Double latitude, longitude;
            String streetName;
            if (place != null){
                streetName = place.name;
                latitude = place.lat;
                longitude = place.lon;
            }
            else if (walkStep != null){
                streetName = walkStep.streetName;
                latitude = walkStep.lat;
                longitude = walkStep.lon;
            }
            else{
                return null;
            }
            String newStreetName;
            Resources resources = applicationContext.getResources();
            if (streetName.equals("bike path")) {
                newStreetName = resources.getString(R.string.street_type_bike_path);
            } else if (streetName.equals("open area")) {
                newStreetName = resources.getString(R.string.street_type_open_area);
            } else if (streetName.equals("path")) {
                newStreetName = resources.getString(R.string.street_type_path);
            } else if (streetName.equals("bridleway")) {
                newStreetName = resources.getString(R.string.street_type_bridleway);
            } else if (streetName.equals("footpath")) {
                newStreetName = resources.getString(R.string.street_type_footpath);
            } else if (streetName.equals("platform")) {
                newStreetName = resources.getString(R.string.street_type_platform);
            } else if (streetName.equals("footbridge")) {
                newStreetName = resources.getString(R.string.street_type_footbridge);
            } else if (streetName.equals("underpass")) {
                newStreetName = resources.getString(R.string.street_type_underpass);
            } else if (streetName.equals("road")) {
                newStreetName = resources.getString(R.string.street_type_road);
            } else if (streetName.equals("ramp")) {
                newStreetName = resources.getString(R.string.street_type_ramp);
            } else if (streetName.equals("link")) {
                newStreetName = resources.getString(R.string.street_type_link);
            } else if (streetName.equals("service road")) {
                newStreetName = resources.getString(R.string.street_type_service_road);
            } else if (streetName.equals("alley")) {
                newStreetName = resources.getString(R.string.street_type_alley);
            } else if (streetName.equals("parking aisle")) {
                newStreetName = resources.getString(R.string.street_type_parking_aisle);
            } else if (streetName.equals("byway")) {
                newStreetName = resources.getString(R.string.street_type_byway);
            } else if (streetName.equals("track")) {
                newStreetName = resources.getString(R.string.street_type_track);
            } else if (streetName.equals("sidewalk")) {
                newStreetName = resources.getString(R.string.street_type_sidewalk);
            } else if (streetName.equals("steps")) {
                newStreetName = resources.getString(R.string.street_type_steps);
            } else if (streetName.startsWith("osm:node")) {
                NumberFormat format = DecimalFormat.getInstance();
                format.setMaximumFractionDigits(4);
                newStreetName = format.format(latitude) + ", " + format.format(longitude);
            } else {
                return streetName;
            }
            OTPGeocoding otpGeocoding = new OTPGeocoding(applicationContext, walkStep, place, this);
            otpGeocoding.execute(latitude.toString(), longitude.toString(), newStreetName);
            return newStreetName;
        }
        return null;
    }

    public static String getLocalizedRelativeDir(RelativeDirection relDir, Resources resources) {
        if (relDir != null) {
            if (relDir.equals(RelativeDirection.CIRCLE_CLOCKWISE)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_circle_clockwise);
            } else if (relDir.equals(RelativeDirection.CIRCLE_COUNTERCLOCKWISE)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_circle_counterclockwise);
            } else if (relDir.equals(RelativeDirection.CONTINUE)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_continue);
            } else if (relDir.equals(RelativeDirection.DEPART)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_depart);
            } else if (relDir.equals(RelativeDirection.ELEVATOR)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_elevator);
            } else if (relDir.equals(RelativeDirection.HARD_LEFT)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_hard_left);
            } else if (relDir.equals(RelativeDirection.HARD_RIGHT)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_hard_right);
            } else if (relDir.equals(RelativeDirection.LEFT)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_left);
            } else if (relDir.equals(RelativeDirection.RIGHT)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_right);
            } else if (relDir.equals(RelativeDirection.SLIGHTLY_LEFT)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_slightly_left);
            } else if (relDir.equals(RelativeDirection.SLIGHTLY_RIGHT)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_slightly_right);
            } else if (relDir.equals(RelativeDirection.UTURN_LEFT)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_uturn_left);
            } else if (relDir.equals(RelativeDirection.UTURN_RIGHT)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_relative_uturn_right);
            }
        }
        return null;
    }

    public static String getLocalizedAbsoluteDir(AbsoluteDirection absDir, Resources resources) {
        if (absDir != null) {
            if (absDir.equals(AbsoluteDirection.EAST)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_absolute_east);
            } else if (absDir.equals(AbsoluteDirection.NORTH)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_absolute_north);
            } else if (absDir.equals(AbsoluteDirection.NORTHEAST)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_absolute_northeast);
            } else if (absDir.equals(AbsoluteDirection.NORTHWEST)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_absolute_northwest);
            } else if (absDir.equals(AbsoluteDirection.SOUTH)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_absolute_south);
            } else if (absDir.equals(AbsoluteDirection.SOUTHEAST)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_absolute_southeast);
            } else if (absDir.equals(AbsoluteDirection.SOUTHWEST)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_absolute_southwest);
            } else if (absDir.equals(AbsoluteDirection.WEST)) {
                return resources.getString(R.string.step_by_step_non_transit_dir_absolute_west);
            }
        }
        return null;
    }

    private ArrayList<Direction> generateTransitDirections(Leg leg) {
        ArrayList<Direction> directions = new ArrayList<Direction>(2);

        directions.add(generateTransitSubdirection(leg, true));
        directions.add(generateTransitSubdirection(leg, false));

        return directions;
    }

    public Direction generateTransitSubdirection(Leg leg, boolean isOnDirection){
        Direction direction = new Direction();
        direction.setRealTimeInfo(leg.realtime);

        //		set icon
        TraverseMode mode = TraverseMode.valueOf(leg.mode);
        int icon;
        String route = leg.route;
        String agencyName = leg.agencyName;
        Place from = leg.from;
        AgencyAndId agencyAndIdFrom = from.stopId;
        Place to = leg.to;
        AgencyAndId agencyAndIdTo = to.stopId;
        Calendar newTime = Calendar.getInstance();
        Calendar oldTime = Calendar.getInstance();

        // Get on HART BUS 6
        String serviceName = agencyName;
        if (serviceName == null) {
            serviceName = "";
        }

        direction.setTransit(true);

        String action, place, agencyAndId, extra = "";

        if (isOnDirection){
            action = applicationContext.getResources()
                    .getString(R.string.step_by_step_transit_get_on);
            place = from.name;
            agencyAndId = " ("
                    + agencyAndIdFrom.getAgencyId() + " " + agencyAndIdFrom.getId() + ")";
            icon = getModeIcon(new TraverseModeSet(mode));
            newTime.setTime(new Date(Long.parseLong(leg.startTime)));
            oldTime.setTime(new Date(newTime.getTimeInMillis()));
            oldTime.add(Calendar.SECOND, - leg.departureDelay);

            // Only onDirection has subdirection (list of stops in between)
            ArrayList<Place> stopsInBetween = new ArrayList<Place>();
            if ((leg.getIntermediateStops() != null) && !leg.getIntermediateStops().isEmpty()) {
                stopsInBetween.addAll(leg.getIntermediateStops());
            }
            else if ((leg.stop != null) && !leg.stop.isEmpty()){
                stopsInBetween.addAll(leg.stop);
            }
            // sub-direction
            ArrayList<Direction> subDirections = new ArrayList<Direction>();
            for (int i = 0; i < stopsInBetween.size(); i++) {
                Direction subDirection = new Direction();

                Place stop = stopsInBetween.get(i);
                AgencyAndId agencyAndIdStop = stop.stopId;
                String subDirectionText = Integer.toString(i + 1) + ". " + stop.name + " (" +
                        agencyAndIdStop.getAgencyId() + " " +
                        agencyAndIdStop.getId() + ")";

                subDirection.setDirectionText(subDirectionText);
                subDirection.setIcon(icon);

                subDirections.add(subDirection);
            }
            direction.setSubDirections(subDirections);

            extra = "\n"  + stopsInBetween.size() + " " + applicationContext.getResources()
                            .getString(R.string.step_by_step_transit_stops_in_between);
        }
        else{
            action = applicationContext.getResources()
                    .getString(R.string.step_by_step_transit_get_off);
            place = to.name;
            agencyAndId = " ("
                    + agencyAndIdTo.getAgencyId() + " " + agencyAndIdTo.getId() + ")";
            icon = -1;
            newTime.setTime(new Date(Long.parseLong(leg.endTime)));
            oldTime.setTime(new Date(newTime.getTimeInMillis()));
            oldTime.add(Calendar.SECOND, - leg.arrivalDelay);
        }

        direction.setIcon(icon);
        direction.setPlace(applicationContext.getResources()
                .getString(R.string.step_by_step_transit_connector_stop_name) + " " + place
                + agencyAndId + extra);
        direction.setService(action + " " + serviceName + " " + mode + " " + route);

        SpannableString oldTimeString;

        if (leg.realtime) {
            CharSequence newTimeString;
            newTimeString = ConversionUtils
                    .getTimeUpdated(applicationContext, leg.agencyTimeZoneOffset,
                            oldTime.getTimeInMillis(), newTime.getTimeInMillis());
            direction.setNewTime(newTimeString);
        }

        oldTimeString = new SpannableString(ConversionUtils
                    .getTimeWithContext(applicationContext, leg.agencyTimeZoneOffset,
                            oldTime.getTimeInMillis(), true));
        direction.setOldTime(oldTimeString);

        return direction;
    }

    public static int getModeIcon(TraverseModeSet mode) {
        if (/*mode.contains(TraverseMode.FERRY) && TODO */
                mode.contains(TraverseMode.BUSISH) &&
                mode.contains(TraverseMode.TRAINISH)) {
            return R.drawable.mode_transit;
        } else if (mode.contains(TraverseMode.BUSISH)) {
            return R.drawable.mode_bus;
        } else if (mode.contains(TraverseMode.TRAINISH)) {
            return R.drawable.mode_train;
        } else if (mode.contains(TraverseMode.FERRY)) {
            return R.drawable.mode_ferry;
        } else if (mode.contains(TraverseMode.GONDOLA)) {
            return R.drawable.mode_ferry;
        } else if (mode.contains(TraverseMode.SUBWAY)) {
            return R.drawable.mode_metro;
        } else if (mode.contains(TraverseMode.TRAM)) {
            return R.drawable.mode_train;
        } else if (mode.contains(TraverseMode.WALK)) {
            return R.drawable.mode_walk;
        } else if (mode.contains(TraverseMode.BICYCLE)) {
            return R.drawable.mode_bike;
        } else {
            return R.drawable.icon;
        }
    }

    public static int getNotificationIcon(TraverseModeSet mode) {
        if (/*mode.contains(TraverseMode.FERRY) && TODO */
                mode.contains(TraverseMode.BUSISH) &&
                        mode.contains(TraverseMode.TRAINISH)) {
            return R.drawable.transit_notification;
        } else if (mode.contains(TraverseMode.BUSISH)) {
            return R.drawable.bus_notification;
        } else if (mode.contains(TraverseMode.TRAINISH)) {
            return R.drawable.train_notification;
        } else if (mode.contains(TraverseMode.FERRY)) {
            return R.drawable.ferry_notification;
        } else if (mode.contains(TraverseMode.SUBWAY)) {
            return R.drawable.metro_notification;
        } else {
            return R.drawable.icon;
        }
    }

    public static int getRelativeDirectionIcon(RelativeDirection relDir) {
        if (relDir.equals(RelativeDirection.CIRCLE_CLOCKWISE)) {
            return R.drawable.circle_clockwise;
        } else if (relDir.equals(RelativeDirection.CIRCLE_COUNTERCLOCKWISE)) {
            return R.drawable.circle_counterclockwise;
        } else if (relDir.equals(RelativeDirection.CONTINUE)) {
            return R.drawable.reldir_continue;
        } else if (relDir.equals(RelativeDirection.DEPART)) {
            return R.drawable.clear;
        } else if (relDir.equals(RelativeDirection.ELEVATOR)) {
            return R.drawable.elevator;
        } else if (relDir.equals(RelativeDirection.HARD_LEFT)) {
            return R.drawable.hard_left;
        } else if (relDir.equals(RelativeDirection.HARD_RIGHT)) {
            return R.drawable.hard_right;
        } else if (relDir.equals(RelativeDirection.LEFT)) {
            return R.drawable.left;
        } else if (relDir.equals(RelativeDirection.RIGHT)) {
            return R.drawable.right;
        } else if (relDir.equals(RelativeDirection.SLIGHTLY_LEFT)) {
            return R.drawable.slightly_left;
        } else if (relDir.equals(RelativeDirection.SLIGHTLY_RIGHT)) {
            return R.drawable.slightly_right;
        } else if (relDir.equals(RelativeDirection.UTURN_LEFT)) {
            return R.drawable.uturn_left;
        } else if (relDir.equals(RelativeDirection.UTURN_RIGHT)) {
            return R.drawable.uturn_right;
        } else {
            return R.drawable.clear;
        }
    }

    /**
     * @return the totalDistance
     */
    public double getTotalDistance() {
        return totalDistance;
    }

    /**
     * @param totalDistance the totalDistance to set
     */
    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    /**
     * @return the totalTimeTraveled
     */
    public double getTotalTimeTraveled() {
        if (legs.isEmpty()) {
            return 0;
        }

        Leg legStart = legs.get(0);
        String startTimeText = legStart.startTime;
        Leg legEnd = legs.get(legs.size() - 1);
        String endTimeText = legEnd.endTime;

        totalTimeTraveled = ConversionUtils.getDuration(startTimeText, endTimeText, applicationContext);

        return totalTimeTraveled;
    }

    @Override
    public void onOTPGeocodingComplete(boolean isStartTextbox, ArrayList<CustomAddress> addressesReturn, boolean geocodingForMarker) {

    }

    @Override
    public void onOTPGeocodingForOtpGeneratedNameComplete(double originalLatitude, double originalLongitude,
                                                          String otpGeneratedString, WalkStep walkStep,
                                                          Place place, ArrayList<CustomAddress> addressesReturn) {
        if (addressesReturn != null && !addressesReturn.isEmpty()){
            CustomAddress selectedAddress = addressesReturn.get(0);
            if (modeMarkers != null){
                for (Map.Entry<Marker, TripInfo> entry : modeMarkers.entrySet()) {
                    if ((entry.getValue().getDestinationLatitude() == originalLatitude)
                            && (entry.getValue().getDestinationLongitude() == originalLongitude)){
                        String newTitle = entry.getKey().getTitle();
                        if (selectedAddress.getStringAddress(true) != null){
                            String newStringAddress = selectedAddress.getStringAddress(true).split("\n")[0];
                            newTitle = newTitle.replace(otpGeneratedString,
                                    newStringAddress);
                            entry.getKey().setTitle(newTitle);
                        }
                    }
                }
            }
            else{
                for (Direction direction : directions) {
                    updateDirectionContents(direction, selectedAddress, originalLatitude,
                            originalLongitude, otpGeneratedString, walkStep, place);
                    if (direction.getSubDirections() != null){
                        for (Direction subDirection : direction.getSubDirections()){
                            updateDirectionContents(subDirection, selectedAddress, originalLatitude,
                                    originalLongitude, otpGeneratedString, walkStep, place);
                        }
                    }
                }
            }
        }
    }

    private void updateDirectionContents(Direction direction, CustomAddress selectedAddress,
                                         double originalLatitude, double originalLongitude,
                                         String otpGeneratedString, WalkStep walkStep, Place place){
        if ((((direction.getOriginLatitude() == originalLatitude)
                && (direction.getOriginLongitude() == originalLongitude)))
                || ((direction.getDestinationLatitude() == originalLatitude)
                && (direction.getDestinationLongitude() == originalLongitude))) {
            if (direction.getDirectionText() != null){
                String newDirectionText = direction.getDirectionText().toString();
                if (selectedAddress.getStringAddress(true) != null) {
                    String newStringAddress = selectedAddress.getStringAddress(true).split("\n")[0];
                    newDirectionText =
                            newDirectionText.replace(otpGeneratedString,
                                    newStringAddress);
                    direction.setDirectionText(newDirectionText);
                    if (direction.getAdapter() != null){
                        direction.getAdapter().notifyDataSetChanged();
                    }

                    for (Leg leg : legs){
                        for (WalkStep walkStepInList : leg.getSteps()){
                            if (walkStepInList.equals(walkStep)){
                                walkStepInList.setStreetName(newStringAddress);
                            }
                        }
                        if (leg.from.equals(place)){
                            leg.from.setName(newStringAddress);
                        }
                        else if (leg.to.equals(place)){
                            leg.to.setName(newStringAddress);
                        }
                    }
                }
            }
        }
    }
}
