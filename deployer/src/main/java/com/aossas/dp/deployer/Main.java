package com.aossas.dp.deployer;

import java.util.Arrays;

public class Main {
	
	public static void main(String[] args) {
		System.out.println("args: " + Arrays.toString(args) + System.lineSeparator());

		String project = args[0];
		String target = args[1];

		Deploy deploy = new Deploy(project, target);
		deploy.deploy();
	}
	
}
