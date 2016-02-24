package com.google.devrel.training.conference.form;

import com.google.devrel.training.conference.domain.Speaker;

import java.util.Date;

/**
 * A simple Java object (POJO) representing a Session form sent from the client.
 */
public class SessionForm {
    private String sessionName;
    private String highlights;
    private Speaker speaker;
    private SessionType typeOfSession;
    private Date startTime;
    private Date endTime;

    public static enum SessionType {
        LECTURE, KEYNOTE, WORKSHOP
    }

    private SessionForm() {} // default constructor is disabled

    /**
     * @param sessionName The name of the conference session
     * @param highlights A small description of this session
     * @param speaker   The person who will give the session
     * @param typeOfSession The type of session
     * @param startTime Date and time when the session starts
     * @param endTime   Date and time when the session ends
     */
    public SessionForm(String sessionName, String highlights, Speaker speaker,
                       SessionType typeOfSession, Date  startTime, Date endTime) {

        this.sessionName = sessionName;
        this.highlights = highlights.toLowerCase();
        this.speaker = speaker;
        this.typeOfSession = typeOfSession;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getSessionName() {
        return sessionName;
    }
    public String getHighlights() {
        return highlights;
    }
    public Speaker getSpeaker() {
        return speaker;
    }
    public SessionType getTypeOfSession() {
        return typeOfSession;
    }
    public Date getStartTime() {
        return startTime;
    }
    public Date getEndTime() {
        return endTime;
    }

}
