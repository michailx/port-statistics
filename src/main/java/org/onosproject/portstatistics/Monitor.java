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
package org.onosproject.portstatistics;

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
import java.util.*;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Series;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigEvent.Type;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.basics.SubjectFactories;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

@Component(immediate = true)
public class Monitor {

    private static final String APP_NAME = "org.onosproject.portstatistics";
    private static final String PRINT_LEGEND = "[Port Statistics] ";  // Used by Logger
    private static final String DATABASE_NAME = "port_statistics";
    private static final String RETENTION_POLICY_NAME = "last_hour";
    private InfluxDB influxDB;

    private final Logger log = getLogger(getClass());
	private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry netcfgRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    private final InternalNetworkConfigListener netcfgListener = new InternalNetworkConfigListener();

    private final ConfigFactory<ApplicationId, MonitorConfig> monitorConfigFactory =
            new ConfigFactory<ApplicationId, MonitorConfig>(
                    SubjectFactories.APP_SUBJECT_FACTORY,
                    MonitorConfig.class, "prediction") {
                @Override
                public MonitorConfig createConfig() {
                    return new MonitorConfig();
                }
            };

    @Activate
    public void activate() {
        netcfgRegistry.addListener(netcfgListener);
        netcfgRegistry.registerConfigFactory(monitorConfigFactory);

        connectToInfluxDB();

        appId = coreService.registerApplication(APP_NAME);
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
        queryInfluxDB();
        log.info(PRINT_LEGEND + "- App Stopped");

        netcfgRegistry.removeListener(netcfgListener);
        netcfgRegistry.unregisterConfigFactory(monitorConfigFactory);
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
                
                if(portstat != null) {
                    log.info(PRINT_LEGEND + "Port " + port.number() + ">> Bytes, Rx: " + portstat.bytesReceived() + ", Tx: " + portstat.bytesSent());
                    insertIntoInfluxDB(d.id().toString(), port.number().toString(), portstat.bytesReceived(), portstat.bytesSent());
                }
                else
                    log.info(PRINT_LEGEND + "Port " + port.number() + ">> unable to read portStats!");

            }
        }
    }

    private void connectToInfluxDB() {
        log.info(PRINT_LEGEND + "########## Connecting to InfluxDB ##########");
        influxDB = InfluxDBFactory.connect("http://127.0.0.1:8086", "root", "root");  // user credentials silently ignored
        // influxDB.setDatabase(DATABASE_NAME);
        log.info(PRINT_LEGEND + "########## Connection to InfluxDB successful ##########");
    }

    private List<QueryResult.Result> queryInfluxDB() {
        Query query = new Query("SELECT * FROM port_counters", DATABASE_NAME);
        List<QueryResult.Result> results = influxDB.query(query).getResults();

        if (results != null && results.get(0) != null && results.get(0).getSeries() != null) {
            for (Series result: results.get(0).getSeries()) log.info(PRINT_LEGEND + " ########## " + result.toString()); 
        }


        return results;
    }

    private void insertIntoInfluxDB(String device_id, String port_id, long rx_bytes, long tx_bytes) {
        // InfluxDB tags: device_id, port_id
        // InfluxDB fields: rx_bytes, tx_bytes
        BatchPoints batchPoints = BatchPoints
            .database(DATABASE_NAME)
            .tag("device_id", device_id)
            .tag("port_id", port_id)
            .retentionPolicy(RETENTION_POLICY_NAME)
            .consistency(ConsistencyLevel.ALL)
            .build();
        Point point1 = Point.measurement("port_counters")
            .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .addField("rx_bytes", rx_bytes)
            .addField("tx_bytes", tx_bytes)
            .build();

        batchPoints.point(point1);
        influxDB.write(batchPoints);
    }

    private class InternalNetworkConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            log.info("NetworkConfigListener " + event.configClass().getClass().getName());
            log.info("Event Type: " + event.type());
            if (event.type() == NetworkConfigEvent.Type.CONFIG_ADDED) {
                Iterator<Map.Entry<String, JsonNode>> ingressPoints = ((MonitorConfig) event.config().get()).getIngressPoints();
                while (ingressPoints.hasNext()) {
                	log.info("LOL");
			    	Map.Entry<String, JsonNode> ingPoint = ingressPoints.next();
    				log.info(ingPoint.getKey() + ":" + ingPoint.getValue());
 				}
            }
        }
    }

    public class MonitorConfig extends Config<ApplicationId> {

    	public Iterator<Map.Entry<String, JsonNode>> getIngressPoints(){
        	ArrayNode parent = (ArrayNode) object.path("ingressPoints");
        	if (parent.isMissingNode()) {
            	return null;
        	} else {
        		log.info("To String:" + parent.toString() + " -- " + parent.getNodeType().toString());
        		Iterator<JsonNode> children = parent.elements();
        		while (children.hasNext()){
        			JsonNode childNode = children.next();
					log.info(childNode.get("ingressSwitch").asText() + " -- " + childNode.get("ingressPort").asInt());
        		}

        		return parent.fields();
        	}
    	}
    }

}

