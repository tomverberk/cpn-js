package com.indevstudio.cpnide.server.controllers;

import com.indevstudio.cpnide.server.createLog.LogCreationConfig;
import com.indevstudio.cpnide.server.createLog.LogCreationController;
import com.indevstudio.cpnide.server.model.*;
import com.indevstudio.cpnide.server.net.PetriNetContainer;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.impl.XLogImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
// @CrossOrigin(origins = "http://localhost:4200")
@CrossOrigin(origins = "*")
@RequestMapping("/api/v2/cpn")
@RestController
@Slf4j
public class SimulatorController {


    @Autowired
    PetriNetContainer _netContainer;

    @PostMapping(value = "/sim/init")
    @ApiOperation(nickname = "Initialize simulation", value = "Init (or ReInit) simulator")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Init success"),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity simInit(@RequestHeader(value = "X-SessionId") String sessionId, @RequestBody Options options) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            _netContainer.InitSimulator(sessionId, options);
            NetInfo netInf = new NetInfo(Arrays.asList(),_netContainer.getEnableTransitions(sessionId), _netContainer.getTokensAndMarking(sessionId));
            return ResponseEntity.status(HttpStatus.OK).body(netInf);
        });
    }

    @GetMapping(value = "/sim/marks")
    @ApiOperation(nickname = "Get Marks", value = "Get Current Marks State")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success", response = PlaceMark[].class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity getMarks(@RequestHeader(value = "X-SessionId") String sessionId) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(_netContainer.returnTokensAndMarking(sessionId)));
    }

    @GetMapping(value = "/sim/transitions/enabled")
    @ApiOperation(nickname = "Get Enabled Transitions", value = "Get Enabeld trsitions")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success", response = String[].class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity getEnabledTransitions(@RequestHeader(value = "X-SessionId") String sessionId) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(_netContainer.returnEnableTrans(sessionId)));
    }

    @GetMapping(value = "/sim/state")
    @ApiOperation(nickname = "Get simulator state", value = "Get simulator state")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = SimInfo.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity getState(@RequestHeader(value = "X-SessionId") String sessionId) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(_netContainer.getState(sessionId)));
    }

    @GetMapping(value = "/sim/get_log")
    @ApiOperation(nickname = "Get created log", value = "Get created log")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "success", response = XLogImpl.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity getLog(@RequestHeader(value = "X-SessionId") String sessionId) {
        XLog log = _netContainer.getLog(sessionId);
        System.out.println(log.getClass());
        return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(_netContainer.getLog(sessionId)));
    }

    @GetMapping(value = "/sim/get_output_path")
    @ApiOperation(nickname = "Get outputPath", value = "Get outputPath")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "success", response = String[].class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity getOutputPath(@RequestHeader(value = "X-SessionId") String sessionId) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            String[] outputPath = {_netContainer.getOutputPath()};
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(outputPath));
        });
    }

    @GetMapping(value = "/sim/step/{transId}")
    @ApiOperation(nickname = "Do simulation step", value = "Do simulation step")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = NetInfo.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity doStep(@RequestHeader(value = "X-SessionId") String sessionId, @PathVariable("transId") String transId) {
        System.out.println(transId);
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            String firedId =_netContainer.makeStep(sessionId, transId);
            SimInfo info = _netContainer.getState(sessionId);
            NetInfo netInf = new NetInfo(Arrays.asList(firedId), _netContainer.getEnableTransitions(sessionId), _netContainer.getTokensAndMarking(sessionId));
            return ResponseEntity.status(HttpStatus.OK).body(netInf);
        });
    }

    @GetMapping(value = "/sim/bindings/{transId}")
    @ApiOperation(nickname = "Show binding", value = "Do simulation step")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = BindingMark[].class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity getBindings(@RequestHeader(value = "X-SessionId") String sessionId, @PathVariable("transId") String transId) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(_netContainer.getBindings(sessionId, transId)));
    }

    @PostMapping(value = "/sim/step_fast_forward")
    @ApiOperation(nickname = "Fast forward stepping", value = "Fast forward stepping")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = MultiStep.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity doStepFastForward(@RequestHeader(value = "X-SessionId") String sessionId, @RequestBody MultiStep stepParams) {
        System.out.println(stepParams);
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            String content = _netContainer.makeStepFastForward(sessionId,stepParams);
            final NetInfo netInf = new NetInfo(Arrays.asList(), _netContainer.getEnableTransitions(sessionId), _netContainer.getTokensAndMarking(sessionId));
            netInf.setExtraInfo(content);
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(netInf));
        });
    }


    @PostMapping(value = "/sim/replication")
    @ApiOperation(nickname = "Replication", value = "Replication - long running op (from mins to hours)")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = Replication.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity doReplication(@RequestHeader(value = "X-SessionId") String sessionId, @RequestBody Replication replicationParams) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            ReplicationResp resp = _netContainer.makeReplication(sessionId,replicationParams);
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(resp));
        });
    }

    @PostMapping(value = "/sim/replication_progress")
    @ApiOperation(nickname = "Replication progress", value = "Replication - long running op (from mins to hours)")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = Replication.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity replicationProgress(@RequestHeader(value = "X-SessionId") String sessionId, @RequestBody Replication replicationParams) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            ReplicationProgressResp resp = _netContainer.getFilesForReplicationProgress(sessionId,replicationParams);
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(resp));
        });
    }


    @PostMapping(value = "/sim/create_log")
    @ApiOperation(nickname = "Create log", value = "Create log - long running op (from mins to hours)")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = LogCreationController.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity doCreateLog(@RequestHeader(value = "X-SessionId") String sessionId, @RequestBody LogCreationConfig logCreationConfig) {
        System.out.println(logCreationConfig);
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            try {
                ReplicationResp resp = _netContainer.makeCreateLog(sessionId, logCreationConfig);
                return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(resp));
            } catch(Exception e){
                System.out.println(HttpStatus.INTERNAL_SERVER_ERROR);
                System.out.println(e);
                return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e));
            }
        });
    }

    @GetMapping(value = "/sim/exist_recorded_events")
    @ApiOperation(nickname = "is log empty", value = "is log empty")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Request success", response = Boolean.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity existRecordedEvents(@RequestHeader(value = "X-SessionId") String sessionId) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            Boolean bool = _netContainer.existRecordedEvents(sessionId);
            System.out.println(bool.getClass());
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(bool));
        });
    }

    @GetMapping(value = "/sim/is_log_empty")
    @ApiOperation(nickname = "is log empty", value = "is log empty")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Request success", response = Boolean.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity isLogEmpty(@RequestHeader(value = "X-SessionId") String sessionId) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            Boolean bool = _netContainer.isLogEmpty(sessionId);
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(bool));
        });
    }

    @PostMapping(value = "/sim/create_log_progress")
    @ApiOperation(nickname = "Create log progress", value = "Create log - long running op (from mins to hours)")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = Replication.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity CreateLogProgress(@RequestHeader(value = "X-SessionId") String sessionId, @RequestBody CreateLogFromReplications createLogParams) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            CreateLogProgressResp resp = _netContainer.getFilesForCreateLogProgress(sessionId,createLogParams);
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(resp));
        });
    }

    @GetMapping(value = "/sim/recordactivities/{bool}")
    @ApiOperation(nickname = "Change recording", value = "Change recording")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "change success"),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity setRecordActivities(@RequestHeader(value = "X-SessionId") String sessionId, @PathVariable("bool") Boolean bool) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            _netContainer.setRecordActivities(bool);
            NetInfo netInf = new NetInfo(Arrays.asList(),_netContainer.getEnableTransitions(sessionId), _netContainer.getTokensAndMarking(sessionId));
            return ResponseEntity.status(HttpStatus.OK).body(netInf);
        });
    }

    @GetMapping(value = "/sim/recordtime/{bool}")
    @ApiOperation(nickname = "Change recording", value = "Change recording")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "change success"),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity setRecordTime(@RequestHeader(value = "X-SessionId") String sessionId, @PathVariable("bool") Boolean bool) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            _netContainer.setRecordTime(bool);
            NetInfo netInf = new NetInfo(Arrays.asList(),_netContainer.getEnableTransitions(sessionId), _netContainer.getTokensAndMarking(sessionId));
            return ResponseEntity.status(HttpStatus.OK).body(netInf);
        });
    }

    @GetMapping(value = "/sim/fileName/{path}")
    @ApiOperation(nickname = "set outputPath", value = "set outputPath")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "set success"),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity setOutputPath(@RequestHeader(value = "X-SessionId") String sessionId, @PathVariable("path") String path) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            _netContainer.setFileName(sessionId, path);
            NetInfo netInf = new NetInfo(Arrays.asList(),_netContainer.getEnableTransitions(sessionId), _netContainer.getTokensAndMarking(sessionId));
            return ResponseEntity.status(HttpStatus.OK).body(netInf);
        });
    }

    @GetMapping(value = "/sim/clear_log")
    @ApiOperation(nickname = "Clear log", value = "Clear log")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "clear success"),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity clearLog(@RequestHeader(value = "X-SessionId") String sessionId) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            _netContainer.clearLog();
            NetInfo netInf = new NetInfo(Arrays.asList(),_netContainer.getEnableTransitions(sessionId), _netContainer.getTokensAndMarking(sessionId));
            return ResponseEntity.status(HttpStatus.OK).body(netInf);
        });
    }





    @PostMapping(value = "/sim/script")
    @ApiOperation(nickname = "Replication", value = "Replication - long running op (from mins to hours)")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = Replication.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity doScript(@RequestHeader(value = "X-SessionId") String sessionId, @RequestBody Replication script) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            ReplicationResp resp = new ReplicationResp();
            resp.setExtraInfo(_netContainer.runScript(sessionId,script));
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(resp));
        });
    }

    @PostMapping(value = "/sim/step_with_binding/{transId}")
    @ApiOperation(nickname = "Step using binding", value = "Do simulation step")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Step success", response = NetInfo.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity doStepWithBinding(@RequestHeader(value = "X-SessionId") String sessionId, @PathVariable("transId") String transId, @RequestBody BindingMark mark) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            _netContainer.makeStepWithBinding(sessionId, mark.getBind_id(), transId);
            NetInfo netInf = new NetInfo(Arrays.asList(),_netContainer.getEnableTransitions(sessionId), _netContainer.getTokensAndMarking(sessionId));
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(netInf));
        });
    }

    @PostMapping(value = "/sim/monitor/new")
    @ApiOperation(nickname = "create monitor", value = "Create monitor")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Create success", response = NewMonitorDescr.class),
                    @ApiResponse(code = 400, message = "Incorrect Request", response = ErrorDescription.class),
                    @ApiResponse(code = 500, message = "Internal error. Object with description", response = ErrorDescription.class)
            })
    public ResponseEntity doStepWithBinding(@RequestHeader(value = "X-SessionId") String sessionId, @RequestBody MonitorDescr monitorDescr) {
        return RequestBaseLogic.HandleRequest(sessionId, () -> {
            return RequestBaseLogic.HandleRequest(sessionId, () -> ResponseEntity.status(HttpStatus.OK).body(_netContainer.getNewMonitor(sessionId, monitorDescr)));
        });
    }


}
