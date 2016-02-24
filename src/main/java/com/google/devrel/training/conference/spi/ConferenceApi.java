package com.google.devrel.training.conference.spi;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.*;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.google.devrel.training.conference.form.SessionForm;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Query;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static com.google.devrel.training.conference.service.OfyService.factory;
import static com.google.devrel.training.conference.service.OfyService.ofy;

/**
 * Defines conference APIs.
 */
@Api(name = "conference", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = {
        Constants.WEB_CLIENT_ID, Constants.ANDROID_CLIENT_ID, Constants.API_EXPLORER_CLIENT_ID, },
        audiences = {Constants.ANDROID_AUDIENCE},
        description = "API for the Conference Central Backend application."
)
public class ConferenceApi {

    private static final Logger LOG = Logger.getLogger(ConferenceApi.class.getName());

    /**
     * Get the display name from the user's email. For example, if the email is
     * lemoncake@example.com, then the display name becomes "lemoncake."
     */
    private static String extractDefaultDisplayNameFromEmail(String email) {
        return email == null ? null : email.substring(0, email.indexOf("@"));
    }

    /**
     * Creates or updates a Profile object associated with the given user object.
     * @param user A User object injected by the cloud endpoints.
     * @param profileForm A ProfileForm object sent from the client form.
     * @return Profile object just created.
     * @throws UnauthorizedException when the User object is null.
     */
    // Declare this method as a method available externally through Endpoints
    @ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
    // The request that invokes this method should provide data that
    // conforms to the fields defined in ProfileForm
    public Profile saveProfile(final User user, ProfileForm profileForm)
            throws UnauthorizedException {

        // If the user is not logged in, throw an UnauthorizedException
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Get the userId and mainEmail
        String mainEmail = user.getEmail();
        String userId = user.getUserId();

        // Get the displayName and teeShirtSize sent by the request.

        String displayName = profileForm.getDisplayName();
        TeeShirtSize teeShirtSize = profileForm.getTeeShirtSize();

        // Get the Profile from the datastore if it exists
        // otherwise create a new one
        Profile profile = ofy().load().key(Key.create(Profile.class, userId))
                .now();

        if (profile == null) {
            // Populate the displayName and teeShirtSize with default values
            // if not sent in the request
            if (displayName == null) {
                displayName = extractDefaultDisplayNameFromEmail(user
                        .getEmail());
            }
            if (teeShirtSize == null) {
                teeShirtSize = TeeShirtSize.NOT_SPECIFIED;
            }
            // Now create a new Profile entity
            profile = new Profile(userId, displayName, mainEmail, teeShirtSize);
        } else {
            // The Profile entity already exists
            // Update the Profile entity
            profile.update(displayName, teeShirtSize);
        }

        // Save the entity in the datastore
        ofy().save().entity(profile).now();

        // Return the profile
        return profile;
    }

    /**
     * Returns a Profile object associated with the given user object. The cloud
     * endpoints system automatically inject the User object.
     * @param user A User object injected by the cloud endpoints.
     * @return Profile object.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
    public Profile getProfile(final User user) throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // load the Profile Entity;
        Key key = Key.create(Profile.class, getUserId(user));

        Profile profile = (Profile) ofy().load().key(key).now();
        return profile;
    }

    /**
     * Gets the Profile entity for the current user or creates it if it doesn't exist
     * @param user The user to get the Id
     * @return user's Profile
     */
    private static Profile getProfileFromUser(User user) {
        // First fetch the user's Profile from the datastore.
        Profile profile = ofy().load().key(
                Key.create(Profile.class, user.getUserId())).now();
        if (profile == null) {
            // Create a new Profile if it doesn't exist.
            // Use default displayName and teeShirtSize
            String email = user.getEmail();
            profile = new Profile(user.getUserId(),
                    extractDefaultDisplayNameFromEmail(email), email, TeeShirtSize.NOT_SPECIFIED);
        }
        return profile;
    }

    /**
     * Creates a new Conference object and stores it to the datastore.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @param conferenceForm A ConferenceForm object representing user's inputs.
     * @return A newly created Conference Object.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
    public Conference createConference(final User user, final ConferenceForm conferenceForm)
            throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Get the userId of the logged in User
        final String userId = user.getUserId();

        // Get the key for the User's Profile
        Key<Profile> profileKey = Key.create(Profile.class, userId);

        // Allocate a key for the conference -- let App Engine allocate the ID
        // Don't forget to include the parent Profile in the allocated ID
        final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);

        // Get the Conference Id from the Key
        final long conferenceId = conferenceKey.getId();

        // Get the existing Profile entity for the current user if there is one
        // Otherwise create a new Profile entity with default values
        //Profile profile = getProfileFromUser(user);

        // Start a transaction to create a new Conference Entity, specifying the user's Profile entity
        // as the parent of the conference
        Conference conference = ofy().transact(new Work<Conference>() {
            @Override
            public Conference run() {
                // Fetch user's Profile.
                Profile profile = getProfileFromUser(user);
                Conference conference = new Conference(conferenceId, userId, conferenceForm);
                // Save Conference and Profile.
                ofy().save().entities(conference, profile).now();
                // Add to the queue the Transaction and Task to execute
                final Queue queue = QueueFactory.getQueue("email-queue");
                queue.add(ofy().getTransaction(),
                        TaskOptions.Builder.withUrl("/tasks/send_confirmation_email")
                                .param("email", profile.getMainEmail())
                                .param("conferenceInfo", conference.toString()));
                return conference;
            }
        });

        return conference;
    }

    /** Code to add at the start of querying for conferences **/
    @ApiMethod(
            name = "queryConferences_nofilters",
            path = "queryConferences_nofilters",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> queryConferences_nofilters() {
        // Find all entities of type Conference
        Query<Conference> query = ofy().load().type(Conference.class).order("name");

        return query.list();
    }

    /**
     * Queries against the datastore with the given filters and returns the result.
     *
     * Normally this kind of method is supposed to get invoked by a GET HTTP method,
     * but we do it with POST, in order to receive conferenceQueryForm Object via the POST body.
     *
     * @param conferenceQueryForm A form object representing the query.
     * @return A List of Conferences that match the query.
     */
    @ApiMethod(
            name = "queryConferences",
            path = "queryConferences",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> queryConferences(ConferenceQueryForm conferenceQueryForm) {
        Iterable<Conference> conferenceIterable = conferenceQueryForm.getQuery();
        List<Conference> result = new ArrayList<>(0);
        List<Key<Profile>> organizersKeyList = new ArrayList<>(0);
        for (Conference conference : conferenceIterable) {
            organizersKeyList.add(Key.create(Profile.class, conference.getOrganizerUserId()));
            result.add(conference);
        }
        // To avoid separate datastore gets for each Conference, pre-fetch the Profiles.
        ofy().load().keys(organizersKeyList);
        return result;
    }

/*
    public List<Conference> filterPlayground() {
        // Query<Conference> query = ofy().load().type(Conference.class).order("name");
        Query<Conference> query = ofy().load().type(Conference.class);
*/
        /*
        // Filter on city
        query = query.filter("city =", "London");
        // query = query.filter("city =", "Default City");

        // Add a filter for topic = "Medical Innovations"
        query = query.filter("topics =", "Medical Innovations");

        // Add a filter for maxAttendees
        query = query.filter("maxAttendees >", 8);
        query = query.filter("maxAttendees <", 10).order("maxAttendees").order("name");

        // Add a filter for month {unindexed composite query}
        // Find conferences in June
        query = query.filter("month =", 6);
        */

        // multiple sort orders
/*
        query = query.filter("city =", "Tokyo").filter("seatsAvailable <", 10).
                filter("seatsAvailable >" , 0).order("seatsAvailable").order("name").
                order("month");


        return query.list();
    }
*/

    /**
     * Returns a list of Conferences that the user created.
     * In order to receive the websafeConferenceKey via the JSON params, uses a POST method.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @return a list of Conferences that the user created.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(
            name = "getConferencesCreated",
            path = "getConferencesCreated",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> getConferencesCreated(final User user) throws UnauthorizedException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        String userId = user.getUserId();
        Key<Profile> userKey = Key.create(Profile.class, userId);
        return ofy().load().type(Conference.class)
                .ancestor(userKey)
                .order("name").list();
    }

    /**
     * Just a wrapper for Boolean.
     * We need this wrapped Boolean because endpoints functions must return
     * an object instance, they can't return a Type class such as
     * String or Integer or Boolean
     */
    public static class WrappedBoolean {

        private final Boolean result;
        private final String reason;

        public WrappedBoolean(Boolean result) {
            this.result = result;
            this.reason = "";
        }

        public WrappedBoolean(Boolean result, String reason) {
            this.result = result;
            this.reason = reason;
        }

        public Boolean getResult() {
            return result;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Returns a Conference object with the given conferenceId.
     *
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return a Conference object with the given conferenceId.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "getConference",
            path = "conference/{websafeConferenceKey}",
            httpMethod = HttpMethod.GET
    )
    public Conference getConference(
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws NotFoundException {
        Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
        Conference conference = ofy().load().key(conferenceKey).now();
        if (conference == null) {
            throw new NotFoundException("No Conference found with key: " + websafeConferenceKey);
        }
        return conference;
    }

    /**
     * Register to attend the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return Boolean true when success, otherwise false
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "registerForConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.POST
    )
    public WrappedBoolean registerForConference(final User user,
                                                @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Get the userId
        final String userId = user.getUserId();

        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
            @Override
            public WrappedBoolean run() {
                try {

                    // Get the conference key
                    // Will throw ForbiddenException if the key cannot be created
                    Key<Conference> conferenceKey = Key.create(websafeConferenceKey);

                    // Get the Conference entity from the datastore
                    Conference conference = ofy().load().key(conferenceKey).now();

                    // 404 when there is no Conference with the given conferenceId.
                    if (conference == null) {
                        return new WrappedBoolean (false,
                                "No Conference found with key: "
                                        + websafeConferenceKey);
                    }

                    // Get the user's Profile entity
                    Profile profile = getProfileFromUser(user);

                    // Has the user already registered to attend this conference?
                    if (profile.getConferenceKeysToAttend().contains(
                            websafeConferenceKey)) {
                        return new WrappedBoolean (false, "Already registered");
                    } else if (conference.getSeatsAvailable() <= 0) {
                        return new WrappedBoolean (false, "No seats available");
                    } else {
                        // All looks good, go ahead and book the seat
                        profile.addToConferenceKeysToAttend(websafeConferenceKey);
                        conference.bookSeats(1);

                        // Save the Conference and Profile entities
                        ofy().save().entities(profile, conference).now();
                        // We are booked!
                        return new WrappedBoolean(true, "Registration successful");
                    }

                }
                catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception");

                }
            }
        });
        // if result is false
        if (!result.getResult()) {
            if (result.getReason().contains("No Conference found with key")) {
                throw new NotFoundException (result.getReason());
            }
            else if (result.getReason() == "Already registered") {
                throw new ConflictException("You have already registered");
            }
            else if (result.getReason() == "No seats available") {
                throw new ConflictException("There are no seats available");
            }
            else {
                throw new ForbiddenException("Unknown exception");
            }
        }
        return result;
    }


    /**
     * Unregister from the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key to unregister
     *                             from.
     * @return Boolean true when success, otherwise false.
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "unregisterFromConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.DELETE
    )
    public WrappedBoolean unregisterFromConference(final User user,
                                                   @Named("websafeConferenceKey")
                                                   final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
            @Override
            public WrappedBoolean run() {
                Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
                Conference conference = ofy().load().key(conferenceKey).now();
                // 404 when there is no Conference with the given conferenceId.
                if (conference == null) {
                    return new  WrappedBoolean(false,
                            "No Conference found with key: " + websafeConferenceKey);
                }

                // Un-registering from the Conference.
                Profile profile = getProfileFromUser(user);
                if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
                    profile.unregisterFromConference(websafeConferenceKey);

                    //Remove all conference's sessionId from the wishlist for this user
                    List<Long> sessionsLst =  conference.getSessionKeys();
                    profile.removeSessionsFromWishList(sessionsLst );

                    conference.giveBackSeats(1);
                    ofy().save().entities(profile, conference).now();
                    return new WrappedBoolean(true);
                } else {
                    return new WrappedBoolean(false, "You are not registered for this conference");
                }
            }
        });
        // if result is false
        if (!result.getResult()) {
            if (result.getReason().contains("No Conference found with key")) {
                throw new NotFoundException (result.getReason());
            }
            else {
                throw new ForbiddenException(result.getReason());
            }
        }
        // NotFoundException is actually thrown here.
        return new WrappedBoolean(result.getResult());
    }

    /**
     * Returns a collection of Conference Object that the user is going to attend.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @return a Collection of Conferences that the user is going to attend.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(
            name = "getConferencesToAttend",
            path = "getConferencesToAttend",
            httpMethod = HttpMethod.GET
    )
    public Collection<Conference> getConferencesToAttend(final User user)
            throws UnauthorizedException, NotFoundException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        Profile profile = ofy().load().key(Key.create(Profile.class, user.getUserId())).now();
        if (profile == null) {
            throw new NotFoundException("Profile doesn't exist.");
        }
        List<String> keyStringsToAttend = profile.getConferenceKeysToAttend();
        List<Key<Conference>> keysToAttend = new ArrayList<>();
        for (String keyString : keyStringsToAttend) {
            keysToAttend.add(Key.<Conference>create(keyString));
        }
        return ofy().load().keys(keysToAttend).values();
    }


    /**
     * Gets the announcements aout of Memecache and sent it to the frontend
     * @return The message to be announced
     */
    @ApiMethod(
            name = "getAnnouncement",
            path = "announcement",
            httpMethod = HttpMethod.GET
    )
    public Announcement getAnnouncement(){
        MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        Object message = memcacheService.get(Constants.MEMCACHE_ANNOUNCEMENTS_KEY);
        if (message != null){
            return new Announcement(message.toString());
        }
        return null;
    }

    /**
     * This is an ugly workaround for null userId for Android clients.
     * @param user A User object injected by the cloud endpoints.
     * @return the App Engine userId for the user.
     */
    private static String getUserId(User user) {
        String userId = user.getUserId();
        if (userId == null) {
            LOG.info("userId is null, so trying to obtain it from the datastore.");
            AppEngineUser appEngineUser = new AppEngineUser(user);
            ofy().save().entity(appEngineUser).now();
            // Begin new session for not using session cache.
            Objectify objectify = ofy().factory().begin();
            AppEngineUser savedUser = objectify.load().key(appEngineUser.getKey()).now();
            userId = savedUser.getUser().getUserId();
            LOG.info("Obtained the userId: " + userId);
        }
        return userId;
    }


    /// SESSION for the conference -----------------------------------

    /** Given a conference, return all sessions
     * @param websafeConferenceKey
     * @return List The conference sessions found
     * @throws UnauthorizedException
     * @throws NotFoundException
     */
    @ApiMethod(
            name = "getConferenceSessions",
            path = "getConferenceSessions/{websafeConferenceKey}",
            httpMethod = HttpMethod.GET
    )
    public List<Session> getConferenceSessions(@Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException {

        Key<Conference> userKey = Key.create(websafeConferenceKey);
        return ofy().load().type(Session.class)
                .ancestor(userKey)
                .list();

    }

    /** Given a conference, return all sessions of a specified type (eg lecture, keynote, workshop)
     * @param websafeConferenceKey
     * @param typeOfSession
     * @return List The conference sessions found
     * @throws UnauthorizedException
     * @throws NotFoundException
     */
    @ApiMethod(
            name = "getConferenceSessionsByType",
            path = "getConferenceSessionsByType/{websafeConferenceKey}/{typeOfSession}",
            httpMethod = HttpMethod.GET
    )
    public List<Session> getConferenceSessionsByType(@Named("websafeConferenceKey") final String websafeConferenceKey,
                                                     @Named("typeOfSession") final SessionForm.SessionType typeOfSession)
            throws UnauthorizedException, NotFoundException {

        Key<Conference> userKey = Key.create(websafeConferenceKey);
        return ofy().load().type(Session.class)
                .ancestor(userKey)
                .filter("typeOfSession =", typeOfSession)
                .list();
    }

    /** Given a speaker, return all sessions given by this particular speaker, across all conferences
     * @param speaker
     * @return List The conference sessions found
     * @throws UnauthorizedException
     * @throws NotFoundException
     */
    @ApiMethod(
            name = "getConferenceSessionsBySpeaker",
            path = "getConferenceSessionsBySpeaker",
            // its a post method in order appengine to insert the json into the Speaker object,
            // otherwise it will arrive a Speaker with null references
            httpMethod = HttpMethod.POST
    )
    public List<Session> getConferenceSessionsBySpeaker(final Speaker speaker)
            throws UnauthorizedException, NotFoundException {

        Query<Session> query = ofy().load().type(Session.class)
                .filter("speaker.firstName =", speaker.getFirstName())
                .filter("speaker.lastName =", speaker.getLastName())
                .order("name");

        return query.list();
    }


    /** Open only to the organizer of the conference
     * @param user
     * @param sessionForm
     * @param websafeConferenceKey
     * @return Session The conference sessions found
     * @throws UnauthorizedException
     * @throws NotFoundException
     * @throws ConflictException
     * @throws ForbiddenException
     */
    @ApiMethod(
            name = "createSession",
            path = "conference/{websafeConferenceKey}/registerSession",
            httpMethod = HttpMethod.POST
    )
    public Session createSession(final User user, final SessionForm sessionForm,
                                 @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException, ConflictException, ForbiddenException {

        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Get the userId
        final String userId = user.getUserId();

        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
            @Override
            public WrappedBoolean run() {
                try {

                    // Get the conference key
                    // Will throw ForbiddenException if the key cannot be created
                    Key<Conference> conferenceKey = Key.create(websafeConferenceKey);

                    // Get the Conference entity from the datastore
                    Conference conference = ofy().load().key(conferenceKey).now();

                    // 404 when there is no Conference with the given conferenceId.
                    if (conference == null) {
                        return new WrappedBoolean (false, "No Conference found with key: " + websafeConferenceKey);
                    }else if( !conference.getOrganizerUserId().equals(userId) ){
                        return new WrappedBoolean (false, "The user is not the organizer. It cannot add sessions.");
                    }

                    // Get current sessions
                    List<Session> sessions = getConferenceSessions(websafeConferenceKey);
                    for(Session session: sessions){
                        // Has the user already registered to attend this conference?
                        if (session.getName().equals(sessionForm.getSessionName())) {
                            return new WrappedBoolean (false, "Already registered");
                        }
                    }


                    // Allocate a key for the session -- let App Engine allocate the ID
                    // Don't forget to include the parent Conference in the allocated ID
                    final Key<Session> sessionKey = factory().allocateId(conferenceKey, Session.class);

                    // Get the Conference Id from the Key
                    final long sessionId = sessionKey.getId();
                    Session session = new Session(sessionId, websafeConferenceKey, sessionForm);

                    conference.addToSessionKeys(session.getId());

                    // Save the Conference and Profile entities
                    ofy().save().entities(session, conference).now();
                    // Session is registered!
                    return new WrappedBoolean(true, "Registration successful");

                } catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception" + e);

                }
            }
        });

        // if result is false
        if (!result.getResult()) {
            if (result.getReason().contains("No Conference found with key")) {
                throw new NotFoundException (result.getReason());
            } else if (result.getReason().equals( "Already registered") ) {
                throw new ConflictException("You have already registered");
            } else if(result.getReason().equals("The user is not the organizer")){
                throw new NotFoundException (result.getReason());
            } else {
                throw new ForbiddenException(result.getReason());
            }
        }

        return null;
    }


    /** adds the session to the user's list of sessions they are interested in attending */
    @ApiMethod(
            name = "addSessionToWishlist",
            path = "session/addWishlist/{websafeSessionKey}",
            httpMethod = HttpMethod.POST
    )
    public WrappedBoolean addSessionToWishlist(final User user, @Named("websafeSessionKey") final String websafeSessionKey)
            throws UnauthorizedException, NotFoundException, ConflictException, ForbiddenException {

        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
            @Override
            public WrappedBoolean run() {
                try {

                    // Get the session key
                    // Will throw ForbiddenException if the key cannot be created
                    Key<Session> sessionKey = Key.create(websafeSessionKey);

                    // Get the Session entity from the datastore
                    Session session = ofy().load().key(sessionKey ).now();

                    // 404 when there is no Conference with the given conferenceId.
                    if (session == null) {
                        return new WrappedBoolean (false, "No Session found with key: " + websafeSessionKey);
                    }

                    // Get the user's Profile entity
                    Profile profile = getProfileFromUser(user);

                    // Has the user already registered to attend this conference?
                    if (profile.getSessionIDsToAttend().contains(session.getId())) {
                        return new WrappedBoolean (false, "Already added");
                    } else {
                        // All looks good, go ahead and book the seat
                        profile.addToSessionIDsToAttend(session.getId());

                        // Save the Conference and Profile entities
                        ofy().save().entities(profile).now();
                        // We are booked!
                        return new WrappedBoolean(true, "Registration successful.");
                    }

                }
                catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception");

                }
            }
        });
        // if result is false
        if (!result.getResult()) {
            if (result.getReason().contains("No Session found with key")) {
                throw new NotFoundException (result.getReason());
            }
            else if (result.getReason() == "Already added") {
                throw new ConflictException("You have already registered");
            }
            else {
                throw new ForbiddenException("Unknown exception");
            }
        }
        return result;
    }

    /** Query for all the sessions in a conference that the user is interested in.
     *  You can decide if they can only add conference they have registered to attend or if the wishlist is open to all conferences. */
    @ApiMethod(
            name = "getSessionsInWishlist",
            path = "session/wishlist/",
            httpMethod = HttpMethod.GET
    )
    public List<Session> getSessionsInWishlist(final User user){

        // Get the user's Profile entity
        Profile profile = getProfileFromUser(user);

        Iterable<Session> sessionIterable  = ofy().load().type(Session.class);
        List<Session> result = new ArrayList<>(0);
        List<Key<Session>> sessionKeyList = new ArrayList<>(0);
        List<Long> sessionIds = profile.getSessionIDsToAttend();
        for (Session session : sessionIterable) {
            if(sessionIds.contains(session.getId())) {
                sessionKeyList.add(Key.create(Session.class, session.getId()));
                result.add(session);
            }
        }
        // To avoid separate datastore gets for each Conference, pre-fetch the Profiles.
        ofy().load().keys(sessionKeyList);
        return result;

    }

    /** Removes the session from the user’s list of sessions they are interested in attending */
    @ApiMethod(
            name = "deleteSessionInWishlist",
            path = "session/wishlist/{websafeSessionKey}",
            httpMethod = HttpMethod.DELETE
    )
    public WrappedBoolean  deleteSessionInWishlist(final User user, @Named("websafeSessionKey") final String websafeSessionKey){

        if(user == null){
            return new WrappedBoolean(false, "Authentication failed");
        }
        if(websafeSessionKey == null){
            return new WrappedBoolean(false, "Bad websafeSessionKey:" + websafeSessionKey);
        }
        Profile profile = getProfileFromUser(user);
        Key<Session> sessionKey = Key.create(websafeSessionKey);

        List<Long> sessionLst = new ArrayList<>();
        sessionLst.add(sessionKey.getId());

        boolean isRemoved = profile.removeSessionsFromWishList(sessionLst);
        if(isRemoved){
            ofy().save().entity(profile);
        }else {
            return new WrappedBoolean(false, "Not ID "+sessionKey.getId()+" was found");
        }

        return new WrappedBoolean(true);  // returns with no errors
    }


}
