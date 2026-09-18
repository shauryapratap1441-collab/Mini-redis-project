package com.Shaurya.miniredis;

public class WrongTypeException extends RuntimeException{
    public WrongTypeException(String message){
        super(message);
    }
}
