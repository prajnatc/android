package com.smartcheckout.poc.models;

/**
 * Created by Swetha_Swaminathan on 11/13/2017.
 */

public class User {

    private String userId;

    public User()
    {

    }

    public  User(String userId){
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}