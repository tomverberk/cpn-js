import { Injectable } from "@angular/core";
import * as X2JS from "../../lib/x2js/xml2json.js";
import { HttpClient, HttpHeaders } from "@angular/common/http";
import { EventService } from "./event.service";
import { Message } from "../common/message";

import { AccessCpnService } from "./access-cpn.service";
import { ModelService } from "./model.service";
import { CpnServerUrl } from "src/cpn-server-url.js";

import { xmlBeautify } from "../../lib/xml-beautifier/xml-beautifier.js";
import { FileService } from "./file.service.js";

import { cloneObject } from "src/app/common/utils";
import { MatDialog } from "@angular/material/dialog";
import { DialogComponent } from "../common/dialog/dialog.component.js";

/**
 * Common service for getting access to project data from all application
 */
@Injectable()
export class ProjectService {
  xmlPrefix =
    '<?xml version="1.0" encoding="iso-8859-1"?>\n<!DOCTYPE workspaceElements PUBLIC "-//CPN//DTD CPNXML 1.0//EN" "http://cpntools.org/DTD/6/cpn.dtd">\n';

  public modelName = "";
  public project = undefined;
  private currentSelectedElement;
  currentPageId;

  public loadingProject = false;

  constructor(
    private eventService: EventService,
    private http: HttpClient,
    private modelService: ModelService,
    private accessCpnService: AccessCpnService,
    private fileService: FileService,
    public dialog: MatDialog
  ) {
    // console.log('ProjectService instance CREATED!');

    this.loadEmptyProject();
  }

  public getProject() {
    return this.project;
  }

  setCurrentElement(element) {
    console.log("Selected element - " + element.name);
    this.currentSelectedElement = element;
  }

  getCurrentElement() {
    return this.currentSelectedElement;
  }

  /**
   * Load project file to ProjectService instance for getting access from all application
   *
   * @param {File} file
   */
  loadProjectFile(file: File) {
    const reader: FileReader = new FileReader();
    reader.readAsText(file);
    reader.onload = (e) => {
      const text: any = reader.result;
      // console.log('File text : ' + text);

      this.loadProjectXml(file.name, text);
    };
  }

  parseXml(xml) {
    let dom = null;
    try {
      dom = new DOMParser().parseFromString(xml, "text/xml");
    } catch (e) {
      dom = null;
    }

    return dom;
  }

  /**
   * Loading project data from XML string, parse XML and converting to project JSON object for all application
   *
   * @param {string} filename
   * @param {string} projectXml
   */
  loadProjectXml(filename: string, projectXml: string) {
    this.modelName = filename;

    // const parser = new DOMParser();
    // const xml = parser.parseFromString(projectXml, 'text/xml');

    // clear project
    this.modelService.loadProject({});
    this.eventService.send(Message.PROJECT_LOAD, {
      project: this.modelService.getProject(),
    });
    this.project = this.modelService.getProject();

    this.loadingProject = true;

    setTimeout(() => {
      const xml = this.parseXml(projectXml);

      if (xml) {
        const x2js = new X2JS();
        const json = x2js.xml_str2json(projectXml);

        console.log("ProjectService, first convert -----> ", json);
        if (json) {
          localStorage.setItem("projectJson-1", JSON.stringify(json));

          this.project = { data: json, name: filename };

          // reset simulator for new project file
          this.accessCpnService.resetSim();

          // reset model service
          this.modelService.markNewModel();

          // load new project
          this.eventService.send(Message.PROJECT_LOAD, {
            project: this.project,
          });
        }
      }

      this.loadingProject = false;
    }, 100);
  }

  loadEmptyProject() {
    const headers = new HttpHeaders()
      .set("Access-Control-Allow-Origin", "*")
      .set("Accept", "application/xml");

    // const modelFile = 'baseModel_ID1008016.cpn';
    // const modelFile = 'discretemodel_task1.cpn';

    // const modelFile = 'erdp.cpn';
    // const modelFile = 'hoponhopoff-color.cpn';
    // const modelFile = 'mscProtocol.cpn'

    // const modelFile = 'emptynet.cpn';

    // const modelFile = 'test-1.cpn';
    // const modelFile = 'test-2.cpn';

    const modelFile = "mynet.cpn";
    // const modelFile = 'mynet2.cpn';
    // const modelFile = 'mynet-sub-1.cpn';

    // const modelFile = 'mynet-colset.cpn';

    // const modelFile = 'mynet-for-sim.cpn';

    // const modelFile = 'mynet-for-sim-sub.cpn';
    // const modelFile = 'mynet-for-sim-sub-2.cpn';
    // const modelFile = 'mynet-for-sim-sub-sub.cpn';

    // const modelFile = 'fuelstation.cpn';
    // const modelFile = 'fuelstation-no-mon.cpn';

    // const modelFile = 'monitors.cpn';

    const url = "./assets/cpn/" + modelFile;
    this.http.get(url, { headers: headers, responseType: "text" }).subscribe(
      (response: any) => {
        // console.log('GET ' + url + ', response = ' + JSON.stringify(response));
        this.loadProjectXml(modelFile, response);
      },
      (error) => {
        console.error("GET " + url + ", error = " + JSON.stringify(error));
      }
    );
  }

  /**
   * Save current project to file
   * @filename - name of file
   * @isNet - True when the to save object is a net, false when the to save object is a log
   */
  public saveProjectToFile(filename: string) {
    if (!filename.toLowerCase().includes(".cpn")) {
      filename += ".cpn";
    }

    this.modelService.fixShapeNames();

    const x2js = new X2JS();
    let xml = x2js.json2xml_str(
      cloneObject(this.modelService.getProjectData())
    );
    xml = `${this.xmlPrefix}\n${xml}`;

    xml = xmlBeautify(xml);

    this.fileService.saveCPNAsText(xml, filename, (filePath) => {
      console.log("fileService.saveAsText", filePath);

      const regex = /[^\/^\\]+$/gm;
      const r = regex.exec(filePath);
      const newFileName = r ? r[0] : undefined;

      this.setModelName(newFileName);
    });
  }


  public getStringProjectDataForSave() {
    const x2js = new X2JS();
    let xml = x2js.json2xml_str(
      cloneObject(this.modelService.getProjectData())
    );
    xml = `${this.xmlPrefix}\n${xml}`;

    return xmlBeautify(xml);
  }

  public setModelName(projectName) {
    this.getProject().name = projectName;
    this.modelService.projectName = projectName;
  }
}
