/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intelligenta.socialgraph;

/**
 *
 * @author adam@filterzilla.camera
 */

public class EdgeScore {
    private Groups group;
    private Multipliers multiplier;  

    public EdgeScore main(Groups g, Multipliers m) {
        this.group = g;
        this.multiplier = m;
        return this;
    }
    
    public enum Groups {
        ZScoreGroupVIP(75),
        ZScoreGroupCloseFriends(50),
        ZScoreGroupFamily(40),
        ZScoreGroupFriends(30),
        ZScoreGroupFollows(20),
        ZScoreGroupAcquaintances(10);
        
        private final Integer value;

        private Groups(Integer value) {
            this.value = value;
        }
    }
    
    public enum Multipliers {
        ZScoreMultiplierSeeFirst(20),
        ZScoreMultiplierVerified(10),
        ZScoreMultiplierNone(5);
        
        private final Integer value;
        
        private Multipliers(Integer value) {
            this.value = value;
        }
    }

    public Integer toInt(){
        return this.group.value*this.multiplier.value;
    }
    
    public String toString(){
        return this.toInt().toString();
    }
};