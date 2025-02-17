import { Injectable } from "@angular/core";
import { EventService } from "./event.service";
import { AccessCpnService } from "./access-cpn.service";
import { ModelService } from "./model.service";
import { Message } from "../common/message";
import { EditorPanelService } from "./editor-panel.service";
import { FileService } from "./file.service";
import * as X2JS from "../../lib/x2js/xml2json.js";
import { cloneObject } from "src/app/common/utils";
import { time } from "console";
import { updatePartiallyEmittedExpression } from "typescript/lib/tsserverlibrary";

@Injectable({
  providedIn: "root",
})
export class SimulationService {
  SINGLE_STEP = 1;
  SINGLE_STEP_CHOOSE_BINDING = 2;
  MULTI_STEP = 3;
  MULTI_STEP_FF = 4;
  REPLICATION = 5;
  CREATE_LOG = 6;

  mode = this.SINGLE_STEP;

  firedTransitionIdList = [];
  firedTransitionBindId;
  firedId;
  outputPath;

  simulatorTime;

  multiStepCount = 0;
  multiStepLastTimeMs = 0;

  simulationConfig = {
    multi_step: {
      steps: 50,
      delay: 500,
    },

    multi_step_ff: {
      steps: 50,
      max_step: 0,
      time_step: 0,
      max_time: 0,
    },

    replication: {
      repeat: 30,
    },

    createLog:{
      caseId: "x",
      startDateTime: "1970-01-01T00:00",
      timeUnit: "days",
      recordedEvents:"complete",
      informationLevelIsEvent: true,
      exportType: "xes",
    }
  };

  public isAnimation = true;
  public isAutoswitchPage = true;

  constructor(
    private eventService: EventService,
    public accessCpnService: AccessCpnService,
    public modelService: ModelService,
    private editorPanelService: EditorPanelService,
    private fileService: FileService,
  ) {
    this.initEvents();
  }

  initEvents() {
    this.eventService.on(Message.SIMULATION_STARTED, () => {});
    this.eventService.on(Message.SIMULATION_STOPED, () =>
      this.onStopSimulation()
    );
    this.eventService.on(Message.SERVER_INIT_SIM_DONE, () =>
      this.onInitSimDone()
    );

    this.eventService.on(Message.SHAPE_HOVER, (event) =>
      this.onShapeHover(event)
    );
    this.eventService.on(Message.SHAPE_SELECT, (event) =>
      this.onShapeSelect(event)
    );
    this.eventService.on(Message.SIMULATION_SELECT_BINDING, (event) =>
      this.onSimulationSelectBinding(event)
    );
    this.eventService.on(Message.SHAPE_RUN_SCRIPT, (message) =>
      this.runscript(message.script)
    );
  }

  public updateTime(){
    //TODO Apparantly this gives back undefined????
    const simState = this.accessCpnService.getSimState();
    const simState2 = this.accessCpnService.getStateData();
    console.log(simState);
    console.log(simState2);
  }

  public setMode(mode) {
    console.log(this.constructor.name, "setMode(), mode = ", mode);

    this.mode = mode;

    switch (this.mode) {
      case this.SINGLE_STEP:
        document.body.style.cursor = "crosshair";
        break;
      case this.SINGLE_STEP_CHOOSE_BINDING:
        document.body.style.cursor = "crosshair";
        break;
      default:
        document.body.style.cursor = "default";
    }
  }

  updateModelEditors() {
    const modelEditorList = this.editorPanelService.getModelEditorList();
    for (const modelEditor of modelEditorList) {
      modelEditor.updateElementStatus();
    }
  }

  onInitSimDone() {
    this.updateModelEditors();
  }

  onStopSimulation() {
    this.setMode(this.SINGLE_STEP);
    this.multiStepCount = 0;
    document.body.style.cursor = "default";
  }

  onShapeHover(event) {
    console.log("onShapeHover(), event = ", event);

    if (!this.accessCpnService.isSimulation) {
      return;
    }

    const element = event.element;
    let setCursor = false;

    if (element && element.type && element.type === "cpn:Transition") {
      switch (this.mode) {
        case this.SINGLE_STEP:
        case this.SINGLE_STEP_CHOOSE_BINDING:
          setCursor = true;
          break;
      }
    }
    // document.body.style.cursor = setCursor ? 'crosshair' : 'defualt';
  }

  onShapeSelect(event) {
    this.firedId = undefined;

    if (!this.accessCpnService.isSimulation) {
      return;
    }

    const element = event.element;
    this.firedTransitionIdList = [];

    if (element && element.type && element.type === "cpn:Transition") {
      // console.log(this.constructor.name, 'onShapeSelect(), this.mode = ', this.mode);

      this.firedId = this.getRealFiredId(element.cpnElement._id);

      if (this.firedId) {
        console.log(
          this.constructor.name,
          "onShapeSelect(), firedId = ",
          this.firedId,
          " IS READY"
        );

        switch (this.mode) {
          case this.SINGLE_STEP:
            this.accessCpnService.doStep(this.firedId).then(
              (success) => {
                this.animateModelEditor();
              },
              (error) => {
                console.error(
                  this.constructor.name,
                  "onShapeSelect(), doStep(), error = ",
                  error
                );
              }
            );
            break;
          case this.SINGLE_STEP_CHOOSE_BINDING:
            this.accessCpnService
              .getBindings(this.firedId)
              .then((data: any) => {
                if (data) {
                  this.eventService.send(Message.SERVER_GET_BINDINGS, {
                    data: data,
                  });
                }
              });
            break;
        }
      }
    }
  }

  /**
   * Get real fired transition id even subst transition clicked
   *
   * @param transId - selected transition id
   */
  getRealFiredId(transId) {
    const trans = this.modelService.getTransById(transId);
    if (!trans) {
      return undefined;
    }
    if (!(trans.subst && trans.subst._subpage)) {
      if (transId in this.accessCpnService.getReadyData()) {
        return transId;
      } else {
        return undefined;
      }
    }
    const subpage = this.modelService.getPageById(trans.subst._subpage);
    if (!subpage) {
      return undefined;
    }
    for (const t of subpage.trans) {
      if (t._id in this.accessCpnService.getReadyData()) {
        return t._id;
      }
    }
    return undefined;
  }

  animateModelEditor() {
    this.updateTime;
    setTimeout(() => {
      const modelEditor = this.editorPanelService.getSelectedModelEditor();
      console.log(
        this.constructor.name,
        "animateModelEditor(), page = ",
        modelEditor
      );

      if (modelEditor) {
        modelEditor.updateElementStatus(this.isAnimation).then(() => {
          console.log(
            this.constructor.name,
            "animateModelEditor(), modelEditor.updateElementStatus(), COMPLETE"
          );
          this.onSimulationAnimateComplete();
        });
      }
    }, 0);
  }

  onSimulationSelectBinding(event) {
    this.updateTime();
    if (!this.accessCpnService.isSimulation) {
      return;
    }

    if (this.firedId && event.binding) {
      console.log(
        this.constructor.name,
        "onSimulationSelectBinding(), this.firedId = ",
        this.firedId
      );

      this.accessCpnService
        .doStepWithBinding(this.firedId, event.binding.bind_id)
        .then(
          (success) => {
            this.animateModelEditor();
          },
          (error) => {
            console.error(
              this.constructor.name,
              "onSimulationSelectBinding(), doStepWithBinding(), error = ",
              error
            );
          }
        );
    }
  }

  onSimulationAnimateComplete() {
    this.updateTime();
    this.updateModelEditors();

    switch (this.mode) {
      case this.MULTI_STEP:
        this.runMultiStep();
        break;
    }
  }

  onSimulationStepDone() {
    this.updateTime();
    return new Promise((resolve, reject) => {
      const firedData = this.accessCpnService.getFiredData();

      const readyData = this.accessCpnService.getReadyData();
      // stop simulation steps if no ready data
      if (Object.keys(readyData).length === 0) {
        this.multiStepCount = 0;
      }

      if (firedData && firedData.length > 0) {
        const page = this.modelService.getPageByElementId(firedData[0]);

        // let needLoadPage = true;
        // const modelEditorList = this.editorPanelService.getModelEditorList();
        // for (const modelEditor of modelEditorList) {
        //   if (modelEditor.pageId === page._id) {
        //     needLoadPage = false;
        //     break;
        //   }
        // }

        if (page && this.isAutoswitchPage) {
          this.editorPanelService
            .getEditorPanelComponent()
            .openModelEditor(page)
            .then(() => {
              resolve();
            });
        } else {
          resolve();
        }
      } else {
        resolve();
      }
    });
  }

  public getAnimationDelay() {
    switch (this.mode) {
      case this.MULTI_STEP:
        return +this.simulationConfig.multi_step.delay;
      default:
        return 500;
    }
  }

  runMultiStep() {
    const timeFromLastStep = new Date().getTime() - this.multiStepLastTimeMs;

    let delay = +this.simulationConfig.multi_step.delay - timeFromLastStep;
    if (delay < 0) {
      delay = 0;
    }

    console.log(
      this.constructor.name,
      "runMultiStep(), this.multiStepCount = ",
      this.multiStepCount
    );
    console.log(this.constructor.name, "runMultiStep(), delay = ", delay);

    setTimeout(() => {
      if (this.multiStepCount > 0) {
        this.accessCpnService.doStep("multistep").then(
          (success) => {
            this.multiStepCount--;
            this.multiStepLastTimeMs = new Date().getTime();
            this.onSimulationStepDone().then(() => {
              this.animateModelEditor();
            });
          },
          (error) => {
            console.error(
              this.constructor.name,
              "runMultiStep(), doStep('multistep'), error = ",
              error
            );
          }
        );
      }
    }, delay);
  }

  runMultiStepFF() {
    console.log(
      this.constructor.name,
      "runMultiStepFF(), this.simulationConfig.multi_step_ff = ",
      this.simulationConfig.multi_step_ff
    );

    const config = this.simulationConfig.multi_step_ff;
    const options = {
      addStep: config.steps,
      untilStep: config.max_step,
      untilTime: config.max_time,
      addTime: config.time_step,
      amount: config.steps,
    };
    this.accessCpnService.doMultiStepFF(options).then(() => {
      const modelEditorList =
        this.editorPanelService.getModelEditorList() || [];
      for (const modelEditor of modelEditorList) {
        modelEditor.updateElementStatus(false);
      }
    });
  }

  runReplication() {
    console.log(
      this.constructor.name,
      "runReplication(), this.simulationConfig.replication = ",
      this.simulationConfig.replication
    );

    const config = this.simulationConfig.replication;
    const options = {
      repeat: "" + config.repeat,
    };
    this.accessCpnService.doReplication(options).then(() => {
      const modelEditorList =
        this.editorPanelService.getModelEditorList() || [];
      for (const modelEditor of modelEditorList) {
        modelEditor.updateElementStatus(false);
      }
    });
  }

  runCreateLog(fileName){
    console.log(
      this.constructor.name,
      "runCreateLog(), this.simulationConfig.CreateLog = ",
      this.simulationConfig.createLog
    );
    const config = this.simulationConfig.createLog; // 30

    const options = {
      caseId: config.caseId,
      startDateTime: config.startDateTime,
      timeUnit: config.timeUnit,
      recordedEvents: config.recordedEvents,
      informationLevelIsEvent: config.informationLevelIsEvent,
      exportType: config.exportType,
    };

    this.accessCpnService.setFileNameOfLog(fileName)
      .then(() => this.accessCpnService.getOutputPathLog())
      .then((result) => {
        this.outputPath = result[0];
        this.accessCpnService.doCreateLog(options)
      .then(() => this.accessCpnService.existRecordedEvents())
      .then((result2) => this.accessCpnService.getIsLogEmpty(result2))
      .then(() => {
        const modelEditorList =
            this.editorPanelService.getModelEditorList() || [];
          for (const modelEditor of modelEditorList) {
            modelEditor.updateElementStatus(false);
          }
          console.log("this.outputPath");
          console.log(this.outputPath);
          this.eventService.send(Message.LOG_SAVED, {path: this.outputPath}); 
      });
    })

    
    
    

    // this.accessCpnService.getIsLogEmpty().then(() => {
    //   const modelEditorList =
    //     this.editorPanelService.getModelEditorList() || [];
    //   for (const modelEditor of modelEditorList) {
    //     modelEditor.updateElementStatus(false);
    //   }
    // });
  }


  setOutputPath(path){
    this.accessCpnService.setFileNameOfLog(path);
  }

  getOutputPath(){
    this.outputPath = this.accessCpnService.getOutputPathLog();
  }

  runscript(script) {
    console.log("runscript(script)", script);
    const options = { repeat: script };
    this.accessCpnService.runScriptOnServer(options).then(() => {
      const modelEditorList =
        this.editorPanelService.getModelEditorList() || [];
      for (const modelEditor of modelEditorList) {
        modelEditor.updateElementStatus(false);
      }
    });
  }

  public async saveLogToFile(filename: string) {
    if (!filename.toLowerCase().includes(".xes")) {
      filename += ".xes";
    }

    const x2js = new X2JS();
    let xml = x2js.json2xml_str(
      cloneObject(this.modelService.getProjectData())
    );

    this.fileService.getLogNameFromElectronService(filename, (newFileName) => {
       this.runCreateLog(newFileName);

       this.accessCpnService.getLog().then((data: any) => {
        if (data) {
          console.log(data);
          console.log("data was returned");
        }
      });
    })
    

    
  }
}
