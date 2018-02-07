/*
 * Copyright 2015-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.port.statistics;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;
import java.util.List;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component(immediate = true)
public class Monitor {

    private static final String APP_NAME = "org.onosproject.port.statistics";
    private static final String PRINT_LEGEND = "[Port Statistics] ";  // Used by Logger

    private final Logger log = getLogger(getClass());
	private ApplicationId appId;  // Do I need this?

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Activate
    public void activate() {
        appId = coreService.registerApplication(APP_NAME);  // Do I need this?
        log.info(PRINT_LEGEND + "- App Started");

        // Schedule
        // https://docs.oracle.com/javase/6/docs/api/java/util/concurrent/ScheduledExecutorService.html
        final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                getMonitorData();
            }
        }, 1, 5, TimeUnit.SECONDS); // 5 seconds interval
    }

    @Deactivate
    public void deactivate() {
        log.info(PRINT_LEGEND + "- App Stopped");
    }

    private void getMonitorData() {
        Iterable<Device> devices = deviceService.getDevices();

        for(Device d : devices)
        {
            log.info(PRINT_LEGEND + "########## Device id " + d.id().toString() + "##########");

            List<Port> ports = deviceService.getPorts(d.id());
            for(Port port : ports)
            {
                PortStatistics portstat = deviceService.getStatisticsForPort(d.id(), port.number());
                PortStatistics portdeltastat = deviceService.getDeltaStatisticsForPort(d.id(), port.number());
                
                if(portstat != null)
                    log.info(PRINT_LEGEND + "Port " + port.number() + ">> portstat bytes recieved: " + portstat.bytesReceived());
                else
                    log.info(PRINT_LEGEND + "Port " + port.number() + ">> unable to read portStats!");

                if(portdeltastat != null)
                    log.info(PRINT_LEGEND + "Port " + port.number() + ">> portdeltastat bytes recieved: " + portdeltastat.bytesReceived());
                else
                    log.info(PRINT_LEGEND + "Port " + port.number() + ">> Unable to read portDeltaStats!");
            }
        }
    }

}

