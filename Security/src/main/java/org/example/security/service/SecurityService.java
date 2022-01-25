package org.example.security.service;

import org.example.image.service.ImageService;
import org.example.security.application.StatusListener;
import org.example.security.data.AlarmStatus;
import org.example.security.data.ArmingStatus;
import org.example.security.data.SecurityRepository;
import org.example.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 * <p>
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private ImageService imageService;
    private SecurityRepository securityRepository;
    private Set<StatusListener> statusListeners = new HashSet<>();
    private boolean catDetection = false;

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     *
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if (catDetection && armingStatus == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        }

        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            ConcurrentSkipListSet<Sensor> sensors = new ConcurrentSkipListSet<>(getSensors());
            sensors.forEach(sensor -> changeSensorActivationStatus(sensor, false));
        }
        securityRepository.setArmingStatus(armingStatus);
        statusListeners.forEach(sl -> sl.sensorStatusChanged());
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     *
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        catDetection = cat;

        if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (!cat && getAllSensorsFromState(false)) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    private boolean getAllSensorsFromState(boolean state) {
        return getSensors().stream().allMatch(sensor -> sensor.getActive() == state);
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     *
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     *
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED || securityRepository.getAlarmStatus() == AlarmStatus.ALARM) {
            return;
        }
        if (securityRepository.getAlarmStatus().equals(AlarmStatus.NO_ALARM)){
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
        if (securityRepository.getAlarmStatus().equals(AlarmStatus.PENDING_ALARM)){
            setAlarmStatus(AlarmStatus.ALARM);
        }

        /*switch(securityRepository.getAlarmStatus()) {
            case NO_ALARM -> {setAlarmStatus(AlarmStatus.PENDING_ALARM);
            }
            case PENDING_ALARM -> {
                setAlarmStatus(AlarmStatus.ALARM);
            }
        }*/
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated() {
        if (securityRepository.getAlarmStatus().equals(AlarmStatus.PENDING_ALARM)){
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        if (securityRepository.getAlarmStatus().equals(AlarmStatus.ALARM)){
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }

        /*switch(securityRepository.getAlarmStatus()) {
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.NO_ALARM);
            case ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }*/
    }

    public void changeSensorActivationStatus(Sensor sensor) {
        AlarmStatus actualAlarmStatus = this.getAlarmStatus();
        ArmingStatus actualArmingStatus = this.getArmingStatus();

        if (actualAlarmStatus == AlarmStatus.PENDING_ALARM && !sensor.getActive()) {
            handleSensorDeactivated();
        } else if (actualAlarmStatus == AlarmStatus.ALARM && actualArmingStatus == ArmingStatus.DISARMED) {
            handleSensorDeactivated();
        }
        securityRepository.updateSensor(sensor);
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     *
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        AlarmStatus alarmStatus = securityRepository.getAlarmStatus();

        if (alarmStatus != AlarmStatus.ALARM) {
            if (active) {
                handleSensorActivated();
            } else if (sensor.getActive()) {
                handleSensorDeactivated();
            }
        }
        System.out.println("BEFORE Sensor " + sensor.getName() + " is now " + sensor.getActive());
        sensor.setActive(active);
        System.out.println("AFTER Sensor " + sensor.getName() + " is now " + (sensor.getActive() ? "active" : "inactive"));
        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     *
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}