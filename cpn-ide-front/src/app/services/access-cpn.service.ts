import { Injectable } from "@angular/core";
import { HttpClient, HttpHeaders } from "@angular/common/http";
import * as X2JS from "src/lib/x2js/xml2json.js";
import { EventService } from "./event.service";
import { Message } from "../common/message";
import { xmlBeautify } from "../../lib/xml-beautifier/xml-beautifier.js";
import { CpnServerUrl } from "src/cpn-server-url";
import { cloneObject, clearArray, nodeToArray } from "../common/utils";
import { ModelService } from "./model.service";
import { SettingsService } from "./settings.service";
import { IpcService } from "./ipc.service";
import { ElectronService } from "ngx-electron";
@Injectable()
export class AccessCpnService {
  public isSimulation = false;
  public isRecordActivities = false;
  public isRecordTime = true;

  public errorData = [];
  public errorIds = [];
  public errorPagesIds = [];
  public warnings = [];
  public replicationProcessingProgress = "0";
  public createLogProcessingProgress = "0";

  public stateData = undefined;
  public readyData = [];
  public readyIds = [];
  public readyPagesIds = [];

  public firedTransIdList = [];

  public tokenData = [];

  public fs = undefined;

  public simulationReport = "";
  public log = "";
  public simulationHtmlFiles = [];
  public logHtmlFiles = [];
  public initNetProcessing;
  public initSimProcessing;
  public fastforwardProcessing = false;
  public replicationProcessing = false;
  public createLogProcessing = false;
  public logProcessing = false;
  public complexVerify = false;
  public isRunningScriptOnServer = false;

  public simInitialized = false;

  sessionId;
  userSessionId;

  constructor(
    private http: HttpClient,
    private eventService: EventService,
    private modelService: ModelService,
    private settingsService: SettingsService,
    private ipcService: IpcService,
    private electronService: ElectronService
  ) {
    this.eventService.on(Message.SERVER_INIT_NET, (event) => {
      console.log("AccessCpnService(), SERVER_INIT_NET, data = ", event);
      if (event) {
        this.initNet(
          event.projectData,
          event.complexVerify || false,
          event.restartSimulator || false
        );
      }
    });
  }

  getErrorData() {
    return this.errorData;
  }

  getTokenData() {
    if (!this.isSimulation) {
      return [];
    }

    return this.tokenData;
  }

  getReadyData() {
    if (!this.isSimulation) {
      return [];
    }

    const readyData = {};
    for (const transId of this.readyData) {
      readyData[transId] = "Ready";

      const page = this.modelService.getPageByElementId(transId);
      if (page) {
        for (const trans of this.modelService.getAllTrans()) {
          if (trans && trans.subst && trans.subst._subpage === page._id) {
            readyData[trans._id] = "Ready";
          }
        }
      }
    }
    return readyData;
  }

  getStateData() {
    if (!this.isSimulation) {
      return undefined;
    }
    return this.stateData;
  }

  getFiredData() {
    if (!this.isSimulation) {
      return [];
    }

    const firedData = [];
    for (const transId of this.firedTransIdList) {
      firedData.push(transId);

      const page = this.modelService.getPageByElementId(transId);
      if (page) {
        for (const trans of this.modelService.getAllTrans()) {
          if (trans && trans.subst && trans.subst._subpage === page._id) {
            firedData.push(trans._id);
          }
        }
      }
    }

    this.firedTransIdList = firedData;
    return this.firedTransIdList;
  }

  getFs() {
    console.log("electronTest get", this.fs);
    return this.fs;
  }

  setFc(fs) {
    console.log("electronTest set", fs);
    this.fs = fs;
    console.log("electronTest after set", this.fs);
  }

  getWarningData() {
    const arr = [];
    Object.keys(this.warnings).forEach((el) => {
      arr[el] = this.warnings[el][0].description;
    });
    return arr;
  }

  updateErrorData(data) {
    clearArray(this.errorIds);
    clearArray(this.errorData);
    clearArray(this.errorPagesIds);

    if (!data.success) {
      for (const id of Object.keys(data.issues)) {
        for (const issue of data.issues[id]) {
          issue.description = issue.description.replace(issue.id + ":", "");
          issue.description = issue.description.replace(issue.id, "");
          issue.description = issue.description.replace(":", "");
          issue.description = issue.description.trim();

          this.errorData[issue.id] = issue.description;
          this.errorIds.push(issue.id);

          const page = this.modelService.getPageByElementId(issue.id);
          if (page && !this.errorPagesIds.includes(page._id)) {
            this.errorPagesIds.push(page._id);
          }
        }
      }
    }
    console.log(
      this.constructor.name,
      "updateErrorData(), this.errorIds = ",
      this.errorIds
    );
    console.log(
      this.constructor.name,
      "updateErrorData(), this.errorData = ",
      this.errorData
    );
    console.log(
      this.constructor.name,
      "updateErrorData(), this.errorPagesIds = ",
      this.errorPagesIds
    );
  }

  updateReadyData(readyData) {
    this.readyData = readyData;

    clearArray(this.readyIds);
    clearArray(this.readyPagesIds);

    for (const id of readyData) {
      this.readyIds.push(id);

      const page = this.modelService.getPageByElementId(id);
      if (page && !this.readyPagesIds.includes(page._id)) {
        this.readyPagesIds.push(page._id);
      }
    }
  }

  updateFiredTrans(firedTransIdList) {
    this.firedTransIdList = firedTransIdList;
  }

  updateTokenData(tokenData) {
    // console.log('updateTokenData(), tokenData = ', JSON.stringify(tokenData));

    clearArray(this.tokenData);

    for (const token of tokenData) {
      this.tokenData.push(token);
    }
  }

  /**
   * Generate new user session
   */
  generateUserSession() {
    // this.userSessionId = 'CPN_USER_SESSION_' + new Date().getTime();
    this.userSessionId = "" + new Date().getTime();
    console.log(
      "generateUserSession(), this.userSessionId = ",
      this.userSessionId
    );
    return this.userSessionId;
  }

  generateSessionId() {
    const id = "CPN_IDE_SESSION_" + new Date().getTime();
    // let id = '' + new Date().getTime();
    // id = 'S' + id.substr(id.length - 6);
    // id = 'SESSION';
    return id;
  }

  /**
   * Get current user session
   */
  getUserSessionId() {
    return this.userSessionId;
  }

  /**
   * Access/CPN API
   */
  initNet(cpnJson, complexVerify = false, restartSimulator = false) {
    return new Promise<void>((resolve, reject) => {
      this.simulationReport = "";

      this.complexVerify = complexVerify;
      console.log(
        "AccessCpnService, initNet(), START, this.initNetProcessing = ",
        this.initNetProcessing
      );
      console.log("AccessCpnService, initNet(), START, cpnJson = ", cpnJson);

      if (this.initNetProcessing) {
        resolve();
        return;
      }

      if (!cpnJson) {
        resolve();
        return;
      }

      if (!this.sessionId) {
        this.sessionId = this.generateSessionId();
      }

      this.modelService.fixShapeNames();

      console.log(
        "AccessCpnService, initNet(), START, this.sessionId = ",
        this.sessionId
      );
      console.log(
        "AccessCpnService, initNet(), START, complexVerify = ",
        complexVerify
      );

      const x2js = new X2JS();
      let cpnXml = x2js.json2xml_str(cloneObject(cpnJson));

      cpnXml = cpnXml.toString("iso-8859-1");
      cpnXml = xmlBeautify(cpnXml);

      // console.log('AccessCpnService, initNet(), START, cpnXml = ', cpnXml);

      this.initNetProcessing = true;
      this.eventService.send(Message.SERVER_INIT_NET_START, {});

      localStorage.setItem("cpnXml", JSON.stringify(cpnXml));

      complexVerify = true;
      restartSimulator = true;

      localStorage.setItem("cpnXml", cpnXml);

      const url = this.settingsService.getServerUrl() + "/api/v2/cpn/init";
      const body = {
        xml: cpnXml,
        complex_verify: complexVerify,
        need_sim_restart: restartSimulator,
      };
      this.http
        .post(url, body, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log("AccessCpnService, initNet(), SUCCESS, data = ", data);
            this.initNetProcessing = false;

            this.frontSideValidation(data);
            this.updateErrorData(data);
            this.warnings = data.warnings || [];
            this.eventService.send(Message.SERVER_INIT_NET_DONE, {
              data: data,
              errorIssues: data.issues,
              warningIssues: data.warnings,
            });

            this.reportReady();

            resolve();
          },
          (error) => {
            console.error("AccessCpnService, initNet(), ERROR, data = ", error);
            this.initNetProcessing = false;
            this.eventService.send(Message.SERVER_INIT_NET_ERROR, {
              data: error,
            });

            reject();
          }
        );
    });
  }

  frontSideValidation(data) {
    const nullError = "Name can`t be empty";
    const sameError = "Nodes have the same name";

    const places = this.modelService.getAllPlaces();
    const transitions = this.modelService.getAllTrans();
    this.addWarnings(data, this.checkNullText(places, "place", nullError));
    this.addWarnings(
      data,
      this.checkNullText(transitions, "transition", nullError)
    );

    const pages = this.modelService.getAllPages();
    // nodeToArray(pages).forEach(page => {
    //   const list = nodeToArray(page.place).concat(nodeToArray(page.trans))
    //   this.addWarnings(data, this.checkSameNames(list, 'page', sameError));
    // });
    nodeToArray(pages).forEach((page) => {
      const list = nodeToArray(page.place);
      this.addWarnings(data, this.checkSameNames(list, "page", sameError));
    });
    nodeToArray(pages).forEach((page) => {
      const list = nodeToArray(page.trans);
      this.addWarnings(data, this.checkSameNames(list, "page", sameError));
    });
  }

  private addWarnings(data, errors: any[]) {
    if (errors.length > 0) {
      data.success = false;
      if (!data.warnings) {
        data["warnings"] = {};
      }
      errors.forEach((err) => {
        if (!data.warnings[err.id]) {
          data.warnings[err.id] = [];
        }
        data.warnings[err.id].push(err);
      });
    }
  }

  private getAllMatches(regex: RegExp, text: string) {
    if (regex.constructor !== RegExp) {
      throw new Error("not RegExp");
    }

    // tslint:disable-next-line:prefer-const
    const res = [];
    let match = null;

    if (regex.global) {
      while ((match = regex.exec(text))) {
        res.push(match);
      }
    } else {
      if ((match = regex.exec(text))) {
        res.push(match);
      }
    }

    return res;
  }

  private checkSameNames(
    checkArray: any[],
    shapeType: string,
    error: string
  ): any[] {
    const list = [];
    if (checkArray.length > 0) {
      const map = new Map();
      checkArray.forEach((el) => {
        if (map.has(el.text)) {
          const obj = map.get(el.text);
          obj.push(el);
          map.set(el.text, obj);
        } else {
          map.set(el.text, [el]);
        }
      });

      map.forEach((v, k) => {
        if (v.length > 1) {
          v.forEach((el) =>
            list.push({ id: el._id, type: shapeType, description: error })
          );
        }
      });
    }
    return list;
  }

  private checkNullText(
    shapes: any[],
    shapeType: string,
    error: string
  ): any[] {
    const list = [];
    if (shapes.length > 0) {
      const err = shapes.filter((place) => place.text === "");
      if (err.length > 0) {
        err.forEach((el) =>
          list.push({ id: el._id, type: shapeType, description: error })
        );
      }
    }
    return list;
  }

  reportReady() {
    this.ipcService.send("app.init.complete");
  }

  // saveErrorData(data) {
  //   this.errorData = [];

  //   if (!data.success) {
  //     for (const id of Object.keys(data.issues)) {
  //       for (const issue of data.issues[id]) {
  //         issue.description = issue.description.replace(issue.id + ':', '');
  //         issue.description = issue.description.replace(issue.id, '');
  //         issue.description = issue.description.replace(':', '');
  //         issue.description = issue.description.trim();
  //         this.errorData[issue.id] = issue.description;
  //       }
  //     }
  //   }

  //   this.updateErrorData(this.errorData);
  // }

  /**
   * Reset simulator initialization flag
   */
  resetSim() {
    this.simInitialized = false;
    this.generateUserSession();
  }

  /**
   * Initialize access/cpn simulator
   */
  initSim() {
    this.simulationReport = "";
    this.isRecordActivities = false;

    if (this.initNetProcessing) {
      return;
    }
    if (this.initSimProcessing) {
      return;
    }

    const cpn = this.modelService.getCpn();
    const fair_options_arr =
      cpn.options && cpn.options.option
        ? cpn.options.option.filter((o) => o._name.includes("fair"))
        : [];
    console.log(
      "AccessCpnService, initSim(), fair_options_arr = ",
      fair_options_arr
    );

    

    const fair_options = {};
    fair_options_arr.forEach((o) => (fair_options[o._name] = o.value.boolean));
    console.log("AccessCpnService, initSim(), fair_options = ", fair_options);

    const body = { options: fair_options };
    console.log("AccessCpnService, initSim(), body = ", body);

    this.simInitialized = false;

    // this.tokenData = [];
    // this.readyData = [];
    this.stateData = undefined;

    if (!this.sessionId) {
      this.sessionId = this.generateSessionId();
    }

    this.initSimProcessing = true;
    this.eventService.send(Message.SERVER_INIT_SIM_START, {});

    console.log(
      "AccessCpnService, initSim(), START, this.sessionId = ",
      this.sessionId
    );

    const url = this.settingsService.getServerUrl() + "/api/v2/cpn/sim/init";
    this.http
      .post(url, body, { headers: { "X-SessionId": this.sessionId } })
      .subscribe(
        (data: any) => {
          this.initSimProcessing = false;
          console.log("AccessCpnService, initSim(), SUCCESS, data = ", data);
          this.simInitialized = true;

          // Get token marks and transition
          if (data) {
            this.updateTokenData(data.tokensAndMark);
            this.updateReadyData(data.enableTrans);
            this.updateFiredTrans(data.firedTrans);
            // this.eventService.send(Message.SIMULATION_STEP_DONE);
          }

          this.eventService.send(Message.SERVER_INIT_SIM_DONE, { data: data });
        },
        (error) => {
          this.initSimProcessing = false;
          console.error("AccessCpnService, initSim(), ERROR, data = ", error);

          this.eventService.send(Message.SERVER_ERROR, { data: error });
        }
      );
  }

  /**
   * Get token/marking state from simulator
   */
  getTokenMarks() {
    console.log(
      "AccessCpnService, getTokenMarks(), this.sessionId = ",
      this.sessionId
    );

    if (!this.simInitialized || !this.sessionId) {
      return;
    }

    this.tokenData = [];

    const url = this.settingsService.getServerUrl() + "/api/v2/cpn/sim/marks";
    this.http
      .get(url, { headers: { "X-SessionId": this.sessionId } })
      .subscribe(
        (data: any) => {
          console.log(
            "AccessCpnService, getTokenMarks(), SUCCESS, data = ",
            data
          );

          this.tokenData = data;

          this.eventService.send(Message.SIMULATION_STEP_DONE);
        },
        (error) => {
          console.error(
            "AccessCpnService, getTokenMarks(), ERROR, data = ",
            error
          );
        }
      );
  }

  /**
   * Do simulation step for transition
   * @param transId - transition id
   */
  doStep(transId) {
    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }

      console.log("AccessCpnService, doStep(), START");

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/step/" + transId; // ID1412328496
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log("AccessCpnService, doStep(), SUCCESS, data = ", data);
            if (data) {
              this.updateTokenData(data.tokensAndMark);
              this.updateReadyData(data.enableTrans);
              this.updateFiredTrans(data.firedTrans);

              console.log("AccessCpnService, doStep(), SUCCESS (2)");

              this.eventService.send(Message.SIMULATION_STEP_DONE);
            }

            this.getSimState();

            resolve();
          },
          (error) => {
            console.error("AccessCpnService, doStep(), ERROR, data = ", error);

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  doStepWithBinding(transId, bindId) {
    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }

      const postData = {
        bind_id: bindId,
      };

      console.log(
        "AccessCpnService, doStepWithBinding(), postData = ",
        postData
      );

      // POST /api/v2/cpn/sim/step_with_binding/{transId}
      const url =
        this.settingsService.getServerUrl() +
        "/api/v2/cpn/sim/step_with_binding/" +
        transId;
      this.http
        .post(url, postData, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, doStepWithBinding(), SUCCESS, data = ",
              data
            );
            if (data) {
              this.updateTokenData(data.tokensAndMark);
              this.updateReadyData(data.enableTrans);

              if (
                transId &&
                (!data.firedTrans || data.firedTrans.length === 0)
              ) {
                data.firedTrans = [transId];
              }
              this.updateFiredTrans(data.firedTrans);
              this.eventService.send(Message.SIMULATION_STEP_DONE);
              this.getSimState();
              resolve();
            }
          },
          (error) => {
            console.error(
              "AccessCpnService, doStepWithBinding(), ERROR, data = ",
              error
            );

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  /**
   *  POST /api/v2/cpn/sim/step_fast_forward
   *  {
   *    "addStep": "string",
   *    "addTime": "string",
   *    "amount": 0,
   *    "untilStep": "string",
   *    "untilTime": "string"
   *  }
   *
   * @param options
   */
  doMultiStepFF(options) {
    this.simulationReport = "";

    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }

      this.fastforwardProcessing = true;

      const postData = options;

      console.log("AccessCpnService, doMultiStepFF(), postData = ", postData);

      const url =
        this.settingsService.getServerUrl() +
        "/api/v2/cpn/sim/step_fast_forward";
      this.http
        .post(url, postData, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, doMultiStepFF(), SUCCESS, data = ",
              data
            );
            if (data) {
              this.updateTokenData(data.tokensAndMark);
              this.updateReadyData(data.enableTrans);
              this.updateFiredTrans(data.firedTrans);

              this.simulationReport = data.extraInfo;

              this.eventService.send(Message.SIMULATION_STEP_DONE);
              this.getSimState();
              this.fastforwardProcessing = false;
              resolve();
            }
          },
          (error) => {
            console.error(
              "AccessCpnService, doMultiStepFF(), ERROR, data = ",
              error
            );

            this.fastforwardProcessing = false;

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  /**
   * POST /api/v2/cpn/sim/replication
   * {
   *  "repeat": "string"
   * }
   * @param options
   */
  doReplication(options) {
    this.simulationReport = "";

    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }

      this.replicationProcessing = true;

      const postData = options;

      console.log("AccessCpnService, doReplication(), postData = ", postData);
      this.gettingProgressReplication(postData);
      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/replication";
      this.http
        .post(url, postData, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, doReplication(), SUCCESS, data = ",
              data
            );
            if (data) {
              // this.updateTokenData(data.tokensAndMark);
              // this.updateReadyData(data.enableTrans);
              // this.updateFiredTrans(data.firedTrans);

              this.simulationReport = data.extraInfo;
              this.simulationHtmlFiles = data.files;
              const regexFindPaths = new RegExp("\\b:\\s.*.html", "g");
              const allPaths = this.simulationReport.match(regexFindPaths);
              console.log(allPaths);
              // tslint:disable-next-line:prefer-const

              let count = 0;
              for (const path of allPaths) {
                this.simulationReport = this.simulationReport.replace(
                  path.substr(2),
                  '<a id="hrefOntResult' +
                  count +
                  '" href="#">' +
                  path.substr(2) +
                  "</a>"
                );
                count++;
              }
              this.eventService.send(Message.SIMULATION_STEP_DONE);

              this.getSimState();

              this.replicationProcessing = false;
              resolve();
              setTimeout(() => {
                for (let i = 0; i < allPaths.length; i++) {
                  const lnk = document.getElementById("hrefOntResult" + i);
                  if (this.electronService.isElectronApp) {
                    lnk.onclick = this.openAsPageInNewTabElectron;
                  } else {
                    lnk.onclick = this.openAsPageInNewTab;
                  }
                  const file = this.simulationHtmlFiles.filter(
                    (value) => value.fileName === allPaths[i].substr(2)
                  )[0];
                  lnk["simulationHtmlFile"] = file ? file.htmlContent : "";
                  console.log("doReplicationTest", lnk);
                }
              }, 1000);
            }
          },
          (error) => {
            console.error(
              "AccessCpnService, doReplication(), ERROR, data = ",
              error
            );

            this.replicationProcessing = false;

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  doCreateLog(options) {
    console.log("start doCreateLog")
    return new Promise<void>((resolve,reject)=> {
      if(!this.simInitialized || !this.sessionId){
        resolve();
        return;
      }

      this.logProcessing = true;

      const postData = options

      console.log("AccessCPNService, doCreateLog(), postData =", postData);
      this.gettingProgressCreateLog(postData);
      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/create_log";
      this.http
        .post(url, postData, {headers: {"X-SessionId": this.sessionId} })
        .subscribe(
          (data:any)=> {
            console.log("DATA STARTS HERE ++++++++++++++++++++++++++")
            console.log(
              "AccessCPService, doCreateLog, SUCCESS, data = ",
              data
            );
            console.log("DATE ENDS HEEERRRE ____________________________")
            if(data) {
              resolve(data);
              // this.log = data.extraInfo;
              // this.logHtmlFiles = data.files
              // const regexFindPaths = new RegExp("\\b:\\s.*.html", "g");
              // const allPaths = this.log.match(regexFindPaths);
              // console.log(allPaths);
              // console.log(this.log);
              
              // this.eventService.send(Message.SIMULATION_STEP_DONE)
              
              // this.getSimState();

              // this.createLogProcessing = false;
              // resolve();
              // setTimeout(() => {
              //   for(let i = 0; i < allPaths.length; i++){
              //     const lnk = document.getElementById("hrefOntResult"+ i);
              //     if( this.electronService.isElectronApp) {
              //       lnk.onclick = this.openAsPageInNewTabElectron;
              //     } else {
              //       lnk.onclick = this.openAsPageInNewTab;
              //     }
              //     const file = this.logHtmlFiles.filter(
              //       (value) => value.fileName === allPaths[i].substr(2)
              //     )[0];
              //     lnk["logHtmlFile"] = file ? file.htmlContent : "";
              //     console.log("doCreateLogTest", lnk);
              //   }
              // }, 1000);
            }
          }, 
          (error) => {
            console.error(
              "AccessCpnService, createLog(), ERROR, data = ",
              error
            );
            this.eventService.send(Message.LOG_UNKWOWN_CASE_ID, { data: error });
            reject(error);
          }
        );
    });
  }

  //TODO MERGE THESE FUNCTIONS
  gettingProgressReplication(options) {
    const self = this;
    const postData = options;
    const url =
      this.settingsService.getServerUrl() +
      "/api/v2/cpn/sim/replication_progress";
    this.http
      .post(url, postData, { headers: { "X-SessionId": this.sessionId } })
      .subscribe((data: any) => {
        self.replicationProcessingProgress = parseFloat(data.progress).toFixed(
          2
        );
        console.log("processing", data);
      });
    if (this.replicationProcessing) {
      setTimeout(function () {
        self.gettingProgressReplication(options);
      }, 2000);
    }
  }

  gettingProgressCreateLog(options) {
    const self = this;
    const postData = options;
    const url =
      this.settingsService.getServerUrl() +
      "/api/v2/cpn/sim/create_log_progress";
    this.http
      .post(url, postData, { headers: { "X-SessionId": this.sessionId } })
      .subscribe((data: any) => {
        self.createLogProcessingProgress = parseFloat(data.progress).toFixed(
          2
        );
        console.log("processing", data);
      });
    if (this.createLogProcessing) {
      setTimeout(function () {
        self.gettingProgressCreateLog(options);
      }, 2000);
    }
  }

  loadHTML(html) {
    const { BrowserWindow } = this.electronService.remote.require("electron");
    const win = new BrowserWindow({ width: 800, height: 600 });
    win.loadURL("file://" + html);
  }

  openAsPageInNewTab($event) {
    // const htmlText = '<h2>This your report html</h2>';
    let path = $event.currentTarget.innerHTML;
    let htmlText = this["simulationHtmlFile"];
    const win = window.open("about:blank", "_blank");
    win.document.write(htmlText);
    win.focus();
  }

  openAsPageInNewTabElectron($event) {
    // const htmlText = '<h2>This your report html</h2>';
    let path = $event.currentTarget.innerHTML;
    const win = window.open(
      "file://" + path,
      "windowName",
      "width=900,height=700",
      false
    );
    win.focus();
  }

  /**
   * Get bindings for transition
   * @param transId - transition id
   */
  getBindings(transId) {
    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }

      const url =
        this.settingsService.getServerUrl() +
        "/api/v2/cpn/sim/bindings/" +
        transId; // ID1412328496
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, getBindings(), SUCCESS, data = ",
              data
            );
            if (data) {
              // this.eventService.send(Message.SERVER_GET_BINDINGS, { data: data });
              resolve(data);
            }
          },
          (error) => {
            console.error(
              "AccessCpnService, getBindings(), ERROR, data = ",
              error
            );

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  getSimState() {
    if (!this.simInitialized || !this.sessionId) {
      return;
    }

    this.stateData = undefined;

    const url = this.settingsService.getServerUrl() + "/api/v2/cpn/sim/state";
    this.http
      .get(url, { headers: { "X-SessionId": this.sessionId } })
      .subscribe(
        (data: any) => {
          console.log(
            "AccessCpnService, getSimState(), SUCCESS, data = ",
            data
          );
          if (data) {
            this.stateData = data;
            this.eventService.send(Message.SIMULATION_UPDATE_STATE);
          }
        },
        (error) => {
          console.error(
            "AccessCpnService, getSimState(), ERROR, data = ",
            error
          );
          this.eventService.send(Message.SERVER_ERROR, { data: error });
        }
      );
  }

  public setIsSimulation(state) {
    this.isSimulation = state;
    this.eventService.send(Message.SIMULATION_UPDATE_STATE);

    if (!this.isSimulation) {
      this.stateData = undefined;
      this.readyData = [];
      this.readyIds = [];
      this.readyPagesIds = [];
      this.tokenData = [];
    }
  }

  public getIsSimulation() {
    return this.isSimulation;
  }

  /**
   * Get token/marking state from simulator
   */
  getXmlFromServer() {
    return new Promise((resolve, reject) => {
      console.log(
        "AccessCpnService, getXmlFromServer(), this.sessionId = ",
        this.sessionId
      );

      if (!this.sessionId) {
        reject("ERROR: sessionId not defined!");
      }

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/xml/export";
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, getXmlFromServer(), SUCCESS, data = ",
              data
            );
            resolve(data);
          },
          (error) => {
            console.error(
              "AccessCpnService, getXmlFromServer(), ERROR, data = ",
              error
            );

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  /**
   * POST /api/v2/cpn/sim/monitor/new
   * {
   *    "nodes": [
   *      "string"
   *    ],
   *    "type": 0
   * }
   *
   * @param transId
   */
  getMonitorDefaults(options) {
    return new Promise<void>((resolve, reject) => {
      if (!this.sessionId) {
        resolve();
        return;
      }

      const postData = options;
      console.log(
        "AccessCpnService, getMonitorDefaults(), postData = ",
        postData,
        this.sessionId
      );

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/monitor/new";
      this.http
        .post(url, postData, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, getMonitorDefaults(), SUCCESS, data = ",
              data
            );
            
            resolve(data);
          },
          (error) => {
            console.error(
              "AccessCpnService, getMonitorDefaults(), ERROR, data = ",
              error
            );

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  runScriptOnServer(script) {
    this.simulationReport = "";

    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }

      this.replicationProcessing = true;

      console.log("AccessCpnService, doReplication(), postData = ", script);

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/script";
      this.http
        .post(url, script, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (response: any) => {
            console.log(
              "AccessCpnService, requestRunScript(), SUCCESS, data = ",
              response
            );
            if (response) {
              this.simulationReport = response.extraInfo;
              this.eventService.send(Message.SIMULATION_STEP_DONE);
              this.getSimState();
              this.replicationProcessing = false;
              resolve();
            }
          },
          (error) => {
            console.error(
              "AccessCpnService, requestRunScript(), ERROR, data = ",
              error
            );
            this.replicationProcessing = false;
            // this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  doClearLog(){
    return new Promise<void>((resolve,reject)=> {
      if(!this.simInitialized || !this.sessionId){
        resolve();
        return;
      }

      console.log("AccessCPNService, doClearLog()");
      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/clear_log";
      this.http
        .get(url, {headers: {"X-SessionId": this.sessionId} })
        .subscribe(
          (data:any)=> {
            resolve();
          },
            (error) => {
            console.error(
              "AccessCPNService, DoCreateLog(), ERROR, data = ",
              + error + 
              "URL = " +
              url
            );

            this.createLogProcessing = false;

            this.eventService.send(Message.SERVER_ERROR, { data: error});
            reject(error);
          }
        );
    });
  }

  setRecordActivities(bool){
    //Reset isRecordTime
    this.isRecordTime = false;
    this.isRecordActivities = bool
    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }

      console.log("AccessCpnService, setRecordActivities(), " + bool);

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/recordactivities/" + bool; // ID1412328496
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            resolve();
          },
          (error) => {
            console.error("AccessCpnService, setRecordActivities(), ERROR, data = ", error);

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  setRecordTime(bool){
    this.isRecordTime = bool
    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }
      console.log("AccessCpnService, setRecordTime(), " + bool);

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/recordtime/" + bool; // ID1412328496
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            resolve();
          },
          (error) => {
            console.error("AccessCpnService, setRecordTime()), ERROR, data = ", error);

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }
  
  
  getIsLogEmpty(existRecordedEvents) {
    return new Promise((resolve, reject) => {
      console.log(
        "AccessCpnService, getIsLogEmpty(), this.sessionId = ",
        this.sessionId
      );

      if (!this.sessionId) {
        reject("ERROR: sessionId not defined!");
      }

      if (!existRecordedEvents){
        resolve(true);
      } else {
        const url =
          this.settingsService.getServerUrl() + "/api/v2/cpn/sim/is_log_empty";
        this.http
          .get(url, { headers: { "X-SessionId": this.sessionId } })
          .subscribe(
            (data: any) => {
              console.log(
                "AccessCpnService, getIsLogEmpty(), SUCCESS, data = ",
                data
              )
              if(data){
                this.eventService.send(Message.LOG_EMPTY_LOG)
              };
              resolve(data)

            },
            (error) => {
              console.error(
                "AccessCpnService, getIsLogEmpty(), ERROR, data = ",
                error
              );

              this.eventService.send(Message.SERVER_ERROR, { data: error });
              reject(error);
            }
          );
      }
    });
  }

  existRecordedEvents() {
    return new Promise((resolve, reject) => {
      console.log(
        "AccessCpnService, existRecordedEvents(), this.sessionId = ",
        this.sessionId
      );

      if (!this.sessionId) {
        reject("ERROR: sessionId not defined!");
      }

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/exist_recorded_events";
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, existRecordedEvents(), SUCCESS, data = ",
              data
            )
            if(!data){
              this.eventService.send(Message.LOG_NO_RECORDED_EVENTS)
            };
            resolve(data)

          },
          (error) => {
            console.error(
              "AccessCpnService, existRecordedEvents(), ERROR, data = ",
              error
            );

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  getLog(){
    return new Promise((resolve, reject) => {
      console.log(
        "AccessCpnService, getLog(), this.sessionId = ",
        this.sessionId
      );

      if (!this.sessionId) {
        reject("ERROR: sessionId not defined!");
      }

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/get_log";
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, getLog(), SUCCESS, data = ",
              data
            )
            resolve(data);
          },
          (error) => {
            console.error(
              "AccessCpnService, getLog(), ERROR, data = ",
              error
            );

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

  getOutputPathLog(){
    return new Promise((resolve, reject) => {
      console.log(
        "AccessCpnService, getOutputPathLog(), this.sessionId = ",
        this.sessionId
      );

      if (!this.sessionId) {
        reject("ERROR: sessionId not defined!");
      }

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/get_output_path";
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            console.log(
              "AccessCpnService, getOutputPathLog(), SUCCESS, data = ",
              data
            )
            resolve(data);
          },
          (error) => {
            console.error(
              "AccessCpnService, getOutputPathLog(), ERROR, data = ",
              error
            );

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }


  setFileNameOfLog(fileName){
    return new Promise<void>((resolve, reject) => {
      if (!this.simInitialized || !this.sessionId) {
        resolve();
        return;
      }

      console.log("AccessCpnService, setFileName(), " + fileName);

      const url =
        this.settingsService.getServerUrl() + "/api/v2/cpn/sim/fileName/" + fileName; // ID1412328496
      this.http
        .get(url, { headers: { "X-SessionId": this.sessionId } })
        .subscribe(
          (data: any) => {
            resolve();
          },
          (error) => {
            console.error("AccessCpnService, setOutputPath(), ERROR, data = ", error);

            this.eventService.send(Message.SERVER_ERROR, { data: error });
            reject(error);
          }
        );
    });
  }

}
