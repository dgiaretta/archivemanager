package info.oais.infomodel.implementation.representationinformation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;

import info.oais.infomodel.interfaces.DataObject;
import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.representationinformation.RepInfoUsage;

public class RepInfoUsageRefImpl implements RepInfoUsage {

	private HashMap<RepresentationInformation, String[]> hm = new HashMap<RepresentationInformation, String[]>();


	@Override
	public String[] getApplication(RepresentationInformation ri) {

		return (String[])hm.get(ri);
	}

	@Override
	public void setApplication(RepresentationInformation ri, String[] appNames) {
		hm.put(ri, appNames);

	}

	@Override
	public void executeApp(String appName, DataObject dato) {
		Process process;
        try {
            process = new ProcessBuilder(appName).start();
            int errorCode = process.waitFor();
// adjust the error code for your use case, different programs might not use 0 as success
            if (errorCode != 0) {
                try (BufferedReader reader = process.errorReader(StandardCharsets.UTF_8)) {
                    throw new RuntimeException(String.format("Program execution failed (code %d): %s", errorCode,
                            reader.lines().collect(Collectors.joining())));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not invoke program.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not wait for process exit.", e);
        }
        try (BufferedReader reader = process.inputReader()) {
        } catch (IOException e) {
            throw new RuntimeException("Could not invoke external program.", e);
        }
	}

	@Override
	public Class<?>[] getInterfaces(RepresentationInformation ri) {

		return ri.getClass().getInterfaces();
	}

}
