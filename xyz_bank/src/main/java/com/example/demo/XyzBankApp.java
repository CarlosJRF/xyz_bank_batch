package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class XyzBankApp {

	public static void main(String[] args) {
		// Cierra el contexto al terminar el job (libera los pools de hilos) y propaga el código de salida
		System.exit(SpringApplication.exit(SpringApplication.run(XyzBankApp.class, args)));
	}

}
