package com.google.devrel.training.conference.domain;

import com.google.appengine.repackaged.com.google.common.collect.ImmutableList;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import java.util.ArrayList;
import java.util.List;


@Entity
@Cache
public class Profile {

	@Id
	String userId;

	String displayName;
	String mainEmail;
	TeeShirtSize teeShirtSize;

	private List<String> conferenceKeysToAttend = new ArrayList<>(0);
	private List<Long> sessionIDsToAttend = new ArrayList<>(0);

    /**
     * Public constructor for Profile.
     * @param userId The user id, obtained from the email
     * @param displayName Any string user wants us to display him/her on this system.
     * @param mainEmail User's main e-mail address.
     * @param teeShirtSize The User's tee shirt size
     * 
     */
    public Profile (String userId, String displayName, String mainEmail, TeeShirtSize teeShirtSize) {
    	this.userId = userId;
    	this.displayName = displayName;
    	this.mainEmail = mainEmail;
    	this.teeShirtSize = teeShirtSize;
    }
	/** Just making the default constructor private. */
	private Profile() {}

	public String getDisplayName() {
		return displayName;
	}

	public String getMainEmail() {
		return mainEmail;
	}

	public TeeShirtSize getTeeShirtSize() {
		return teeShirtSize;
	}

	public String getUserId() {
		return userId;
	}

	public List<String> getConferenceKeysToAttend() {
		return ImmutableList.copyOf(conferenceKeysToAttend);
	}
	public void addToConferenceKeysToAttend(String conferenceKey){
		conferenceKeysToAttend.add(conferenceKey);
	}

	/**
	 * Removes the conferenceKey from te registered conferenceKeysToAttend
	 * @param conferenceKey A websafe String representation of the ConferenceKey
	 * @throws IllegalArgumentException
     */
	public void unregisterFromConference(String conferenceKey) throws IllegalArgumentException{
		if (conferenceKeysToAttend.contains(conferenceKey)) {
			conferenceKeysToAttend.remove(conferenceKey);
		}else{
			throw new IllegalArgumentException("Key was not found.");
		}
	}


	public List<Long> getSessionIDsToAttend() {
		return ImmutableList.copyOf(sessionIDsToAttend );
	}
	public void addToSessionIDsToAttend(Long sessionKey){
		sessionIDsToAttend .add(sessionKey);
	}

	/**
	 * Removes the sessionKey from te registered sessionKeysToAttend
	 * @param sessionKeys A websafe String representation of the sessionKey
	 * @throws IllegalArgumentException
	 */
	public boolean removeSessionsFromWishList(List<Long> sessionKeys) throws IllegalArgumentException{
		if ( sessionKeys != null ) {
			return conferenceKeysToAttend.removeAll(sessionKeys);
		}else{
			throw new IllegalArgumentException("Key was not found.");
		}
	}

	public void update(String dispName, TeeShirtSize teeShirtSize) {
		if( dispName != null ){
			this.displayName = dispName;
		}
		if( teeShirtSize != null ) {
			this.teeShirtSize = teeShirtSize;
		}
	}
}
