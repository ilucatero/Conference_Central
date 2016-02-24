package com.google.devrel.training.conference.domain;

import com.google.api.server.spi.config.AnnotationBoolean;
import com.google.api.server.spi.config.ApiResourceProperty;
import com.google.common.base.Preconditions;
import com.google.devrel.training.conference.form.SessionForm;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.*;

import java.util.Date;

@Entity
@Cache
public class Session {

    @Id
    private long id;

    @Index
    private String name;
    private String highlights;
    @Index
    private Speaker speaker;
    @Index
    private SessionForm.SessionType typeOfSession;
    private Date startDateTime;
    private long duration;

    @Parent
    @ApiResourceProperty(ignored = AnnotationBoolean.TRUE)
    private Key<Conference> conferenceKey;


    private Session() {}

    public Session(final long id, final String websafeConferenceKey, final SessionForm sessionForm) {

        Preconditions.checkNotNull(sessionForm.getSessionName(), "The name is required");

        this.id = id;
        this.name = sessionForm.getSessionName();
        this.highlights = sessionForm.getHighlights();
        this.speaker = sessionForm.getSpeaker();
        this.typeOfSession = sessionForm.getTypeOfSession();
        this.startDateTime = sessionForm.getStartTime();
        this.duration = (sessionForm.getStartTime().getTime() - sessionForm.getEndTime().getTime());

        this.conferenceKey = Key.create(websafeConferenceKey);

    }

    public long getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getHighlights() {
        return highlights;
    }
    public Speaker getSpeaker() {
        return speaker;
    }
    public long getDuration() {
        return duration;
    }
    public SessionForm.SessionType getTypeOfSession() {
        return typeOfSession;
    }
    public Date getStartDateTime() {
        return startDateTime;
    }


    @ApiResourceProperty(ignored = AnnotationBoolean.TRUE)
    public Key<Conference> getConferenceKey() {
        return conferenceKey;
    }
    // Get a String version of the key
    public String getWebsafeKey() {
        return Key.create(conferenceKey, Session.class, id).getString();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("Id: " + id + "\n")
                .append("Name: ").append(name).append("\n");
        if (highlights != null) {
            stringBuilder.append("Highlights: ").append(highlights).append("\n");
        }
        if (speaker != null) {
            stringBuilder.append("Speaker: ").append(speaker.toString()).append("\n");
        }
        if (typeOfSession != null) {
            stringBuilder.append("TypeOfSession: ").append(typeOfSession.toString()).append("\n");
        }
        if (startDateTime != null) {
            stringBuilder.append("StartDateTime: ").append(startDateTime.toString()).append("\n");
        }
        stringBuilder.append("Duration: ").append(duration+"").append("\n");

        return stringBuilder.toString();
    }

}
