/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intelligenta.socialgraph;

public class Verbs {
    
    public enum Action {
        SHARE("share"),
        LIKE("like"),
        LOVE("love"),
        FAV("fav"),
        HUG("hug");
        
        private final String text;
        
        /**
        * @param text
        */
        private Action(String value) {
            this.text = value;
        }
        
//        @Override
//        public String toString() {
//            return text;
//        }
        
        public String noun() {
            return text;
        }
        
        public String plural() {
            return text + "s";
        }
        
        public String pastTense(){
            
            if (text.endsWith("e"))
                return text + "d";
            else if (text.endsWith("g"))
                return text + "ged";
            else 
                return text + "ed";
        }
        
        public String key(){
            return plural() + ":";
        }
    }
}