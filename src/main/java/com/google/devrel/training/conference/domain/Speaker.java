package com.google.devrel.training.conference.domain;

public class Speaker {

    private String firstName;
    private String lastName;

    // Introducing the dummy constructor to avoid issues with JSON conversion
    private Speaker() { }

    /**
     * @param firstName
     * @param lastName
     */
    public Speaker(String firstName, String lastName){
        this.firstName = firstName.toLowerCase();
        this.lastName = lastName.toLowerCase();
    }

    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }


    @Override
    public String toString(){
        return "FirstName: " + firstName + "\n" + "LastName: " + lastName;
    }
}
