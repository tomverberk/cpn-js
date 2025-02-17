import { Injectable } from "@angular/core";
import { EventService } from "./event.service";
import { Message } from "../common/message";
import { SettingsService } from "../services/settings.service";
import Diagram from "diagram-js";

import { getDefText } from "../../lib/cpn-js/features/modeling/CpnElementFactory";

import {
  nodeToArray,
  addNode,
  arrayMove,
  getNextId,
  cloneObject,
} from "../common/utils";
import {
  DataCollectionMonitorTemplate,
  BreakpointMonitorTemplate,
  UserDefinedMonitorTemplate,
  WriteInFileMonitorTemplate,
  MarkingSizeMonitorTemplate,
  ListLengthDataCollectionMonitorTemplate,
  CountTransitionOccurrencesMonitorTemplate,
  PlaceContentBreakPointMonitorTemplate,
  TransitionEnabledBreakPointMonitorTemplate,
  MonitorType,
} from "../common/monitors";
import { parseDeclarartion } from "../project-tree/project-tree-declaration-node/declaration-parser";
import { DEFAULT_PAGE } from "../common/default-data";
import { element } from "protractor";
import { ColorDeclarationsPipe } from "../pipes/color-declarations.pipe";
import { ModelEditorComponent } from "../model-editor/model-editor.component";
import { ModelEditorToolbarComponent } from "../model-editor/model-editor-toolbar/model-editor-toolbar.component";
/**
 * Common service for getting access to project data from all application
 */
@Injectable()
export class ModelService {
  diagram: Diagram;
  monitorType = MonitorType;

  private isLoaded = false;

  public project = undefined;
  public projectData = undefined;
  public projectName = "";
  public logName = "";
  public selectedElements = [];

  private backupModel = [];
  private redoBackupModel;
  private modelCase = [];

  countNewItems = 0;
  paramsTypes = ["ml", "color", "var", "globref"];

  constructor(
    private eventService: EventService,
    private settings: SettingsService,
    //private modelEditorComponent: ModelEditorComponent,
    //private modelEditor: ModelEditorToolbarComponent,
  ) {
    console.log("ModelService instance CREATED!");

    this.modelCase["cpn:Place"] = "place";
    this.modelCase["cpn:Transition"] = "trans";
    this.modelCase["cpn:Connection"] = "arc";
    this.modelCase["cpn:Label"] = "Aux";
    this.modelCase["bpmn:Process"] = "trans";
    this.modelCase["place"] = "place";
    this.modelCase["trans"] = "trans";
    this.modelCase["arc"] = "arc";
    this.modelCase["label"] = "label";

    this.eventService.on(Message.PROJECT_LOAD, (event) => {
      if (event.project) {
        this.loadProject(event.project);
        this.eventService.send(Message.SERVER_INIT_NET, {
          projectData: this.getProjectData(),
          complexVerify: true,
          restartSimulator: true,
        });
      }
    });

    // this.eventService.on(Message.MODEL_RELOAD, () => {
    //   // this.loadProject(this.getProject());
    // });

    this.eventService.on(Message.PAGE_CREATE_SUBST, (event) => {
      console.log("Message.PAGE_CREATE_SUBST, event = ", event);

      const subpageCpnElement = this.createSubpage(
        event.cpnElement,
        event.subPageName,
        event.subPageId
      );

      this.eventService.send(Message.PAGE_UPDATE_SUBST, {
        cpnElement: event.cpnElement,
        subpageName: subpageCpnElement.pageattr._name,
      });
    });
  }

  markNewModel() {
    this.isLoaded = false;
  }

  markOpenedModel() {
    this.isLoaded = true;
  }

  isModelLoaded() {
    return this.isLoaded;
  }

  /**
   * Load project
   */
  public loadProject(project) {
    console.log("ModelService.loadProject(), project = ", project);

    this.project = project;
    this.projectData = project.data;
    this.projectName = project.name;
    this.logName = project.logName;

    this.updatePlaceTypes();
    this.updateInstances();
    this.updateBinders();

    localStorage.setItem("projectJson", JSON.stringify(this.projectData));
  }

  public getProject() {
    return this.project;
  }

  public getProjectData() {
    return this.projectData;
  }

  /**
   * Get root cpnet element from CPN project JSON object
   * @returns - cpnElement for cpnet element
   */
  getCpn() {
    let cpnet;
    if (this.projectData) {
      if (this.projectData.workspaceElements) {
        if (this.projectData.workspaceElements instanceof Array) {
          for (const workspaceElement of this.projectData.workspaceElements) {
            if (workspaceElement.cpnet) {
              cpnet = workspaceElement.cpnet;
              break;
            }
          }
        } else {
          if (this.projectData.workspaceElements.cpnet) {
            cpnet = this.projectData.workspaceElements.cpnet;
          }
        }
      }
    }
    return cpnet;
  }

  getMonitorsRoot() {
    const cpnet = this.getCpn();
    if (cpnet) {
      if (!cpnet.monitorblock) {
        cpnet.monitorblock = {};
      }
      return cpnet.monitorblock;
    }
    return undefined;
  }

  /**
   * Delete any cpn element from model json
   *
   * @param cpnElement
   */
  deleteFromModel(cpnElement) {
    const id = cpnElement._id;

    const e = this.findCpnElementById(
      undefined,
      undefined,
      this.projectData,
      id
    );

    console.log("deleteFromModel(), e = ", e);

    if (e) {
      if (e.cpnParentElement instanceof Array) {
        e.cpnParentElement.splice(e.cpnParentElement.indexOf(e.cpnElement), 1);
        if (e.cpnParentElement.length === 0) {
          // console.log('deleteFromModel(), delete e = ', e);
          if (e.cpnGrandParentElement) {
            this.deleteFromObject(e.cpnGrandParentElement, e.cpnParentElement);
          }
        }
      } else if (e.cpnParentElement instanceof Object) {
        this.deleteFromObject(e.cpnParentElement, e.cpnElement);
      }
    }

    if (cpnElement.subst) {
      this.updateInstances();
    }
  }

  /**
   * Delete object from it's parent
   *
   * @param cpnParentElement
   * @param cpnElement
   */
  deleteFromObject(cpnParentElement, cpnElement) {
    if (cpnParentElement instanceof Object) {
      for (const key of Object.keys(cpnParentElement)) {
        if (cpnParentElement[key] === cpnElement) {
          delete cpnParentElement[key];
        }
      }
    }
  }

  /**
   * Find cpn object in json object tree
   *
   * @param cpnGrandParentElement
   * @param cpnParentElement
   * @param cpnElement
   * @param id
   */
  findCpnElementById(cpnGrandParentElement, cpnParentElement, cpnElement, id) {
    if (cpnElement instanceof Object || cpnElement instanceof Array) {
      // console.log('getCpnElementById(), cpnElement = ', cpnElement);

      if (cpnElement._id === id) {
        return {
          cpnGrandParentElement: cpnGrandParentElement,
          cpnParentElement: cpnParentElement,
          cpnElement: cpnElement,
        };
      }

      for (const k of Object.keys(cpnElement)) {
        const e = this.findCpnElementById(
          cpnParentElement,
          cpnElement,
          cpnElement[k],
          id
        );
        if (e) {
          return {
            cpnGrandParentElement: e.cpnGrandParentElement,
            cpnParentElement: e.cpnParentElement,
            cpnElement: e.cpnElement,
          };
        }
      }
    }
    return undefined;
  }

  addElementJsonOnPage(cpnElement, pageId, type, _modeling) {
    console.log("addElementJsonOnPage()", cpnElement, pageId, type);

    const page = this.getPageById(pageId);
    console.log("addElementJsonOnPage(), page = ", page);

    if (page[this.modelCase[type]] instanceof Array) {
      page[this.modelCase[type]].push(cpnElement);
    } else {
      if (page[this.modelCase[type]]) {
        const currentElem = page[this.modelCase[type]];
        page[this.modelCase[type]] = [currentElem, cpnElement];
      } else {
        page[this.modelCase[type]] = [cpnElement];
      }
    }

    if (cpnElement.subst) {
      this.updateInstances();
    }

    // this.eventService.send(Message.MODEL_CHANGED);
  }

  instaceForTransition(id, isRoot) {
    return !isRoot
      ? { _id: getNextId(), _trans: id }
      : { _id: getNextId(), _page: id };
  }

  /**
   * Correct Place types. It should be UNIT by default if empty
   */
  updatePlaceTypes() {
    const defPlaceType = this.settings.appSettings["type"];

    const allPlaces = this.getAllPlaces();
    for (const p of allPlaces) {
      if (p.type) {
        let text;
        if (typeof p.type.text === "object") {
          text = p.type.text.__text || "";
        } else {
          text = p.type.text || "";
        }

        if (text === "") {
          p.type.text = getDefText(defPlaceType);
        }
      }
    }
  }

  updateBinders(rootInstanceId = null) {
    const cpnet = this.getCpn();

    if (!cpnet) {
      return;
    }

    cpnet.binders = {}; // binders;
  }

  updateInstances() {
    const cpnet = this.getCpn();

    if (!cpnet) {
      return;
    }

    const rootPages = this.getRootPages();

    const instances = [];

    let rootInstanceId;

    for (const p of rootPages) {
      if (!rootInstanceId) {
        rootInstanceId = p._id + "itop";
      }

      const instance: any = {
        _id: p._id + "itop",
        _page: p._id,
      };

      const subinstances = this.getSubInstances(p);
      if (subinstances) {
        instance.instance = subinstances;
      }

      instances.push(instance);
    }

    if (instances.length > 1) {
      cpnet.instances = { instance: instances };
    } else if (instances.length === 1) {
      cpnet.instances = { instance: instances[0] };
    } else if (cpnet.instances) {
      delete cpnet.instances;
    }

    // this.updateBinders(rootInstanceId);

    this.updateMonitorInstances();
  }

  updateMonitorInstances(monitorblock = this.getCpn().monitorblock) {
    if (monitorblock) {
      // iterate all monitors
      for (const monitor of nodeToArray(monitorblock.monitor)) {
        // iterate all monitor nodes
        for (const node of nodeToArray(monitor.node)) {
          // if node refer to element
          if (node._idref) {
            // try to get page for element
            const nodePage = this.getPageByElementId(node._idref);

            // if page defined
            if (nodePage) {
              // try to get instance for page
              const inst = this.getInstance(nodePage._id);

              // if instance defined
              if (inst) {
                // set new instance id reference
                node._pageinstanceidref = inst._id;
              }
            }
          }
        }
      }
    }
  }

  /**
   * Find sub instances for page
   */
  getSubInstances(page) {
    console.log(this.constructor.name, "getSubInstances(), page = ", page);

    if (!page) {
      return undefined;
    }

    const instances = [];

    for (const t of nodeToArray(page.trans)) {
      if (t && t.subst) {
        const instance: any = {
          _id: t._id + "ia",
          _trans: t._id,
        };

        const subpage = this.getPageById(t.subst._subpage);
        if (subpage) {
          const subinstances = this.getSubInstances(subpage);
          if (subinstances) {
            instance.instance = subinstances;
          }
        }

        instances.push(instance);
      }
    }

    if (instances.length === 0) {
      return undefined;
    }
    if (instances.length === 1) {
      return instances[0];
    }
    return instances;
  }

  getInstance(pageId, instances = this.getCpn().instances) {
    console.log(
      this.constructor.name,
      "getInstance(), pageId, instances = ",
      pageId,
      instances
    );

    for (const inst of nodeToArray(instances)) {
      if (inst._page === pageId) {
        return inst;
      }

      if (inst._trans) {
        const trans = this.getTransById(inst._trans);
        if (trans && trans.subst && trans.subst._subpage === pageId) {
          return inst;
        }
      }

      if (inst.instance) {
        const inst2 = this.getInstance(pageId, inst.instance);
        if (inst2) {
          return inst2;
        }
      }
    }
    return undefined;
  }

  moveNonModelJsonElement(element, parent, target, index, type) {
    // console.log(this.constructor.name, 'moveNonModelJsonElement(), element = ', element);

    const addelemToEntry = (entry) => {
      if (target[entry]) {
        if (!(target[entry] instanceof Array)) {
          target[entry] = [target[entry]];
        }
        if (element instanceof Array) {
          for (const el of element) {
            target[entry].splice(++index, 0, el);
          }
        } else {
          target[entry].splice(index, 0, element);
        }
      } else if (element instanceof Array) {
        target[type] = element;
      } else {
        target[type] = [element];
      }
    };

    if (type === "page") {
    } else if (!type) {
      if (target.block) {
        if (target.block instanceof Array) {
          target.block.splice(index, 0, element);
        } else {
          target.block = [target.block];
          target.block.splice(index, 0, element);
        }
      } else if (element instanceof Array) {
        target.block = element;
      } else {
        target.block = [element];
      }
      if (parent.block instanceof Array) {
        for (let i = 0; i < parent.block.length; i++) {
          if (parent.block[i]._id === element._id) {
            parent.block.splice(i, 1);
            break;
          }
        }
      } else {
        parent.block = [];
      }
    } else if (this.paramsTypes.includes(type)) {
      if (parent[type] instanceof Array && parent[type].length > 0) {
        for (let i = 0; i < parent[type].length; i++) {
          if (parent[type][i]._id === element._id) {
            parent[type].splice(i, 1);
            break;
          }
        }
      } else {
        delete parent[type];
      }
      addelemToEntry(type);
    } else {
      if (parent.block instanceof Array) {
        for (let i = 0; i < parent.block.length; i++) {
          if (parent.block[i]._id === element._id) {
            parent.block.splice(i, 1);
          }
        }
      } else {
        parent.block = [];
      }
      addelemToEntry("block");
    }
  }

  changeLabelText(label, text, pageId) {
    if (label && label.text) {
      label.text.__text = text;
    }
  }

  changePageName(pageId, name) {
    const changedPage = this.getPageById(pageId);
    if (changedPage) {
      changedPage.pageattr._name = name;
    }
  }

  createNewPage(page) {
    const cpnet = this.getCpn();

    if (cpnet.page instanceof Array) {
      cpnet.page.push(page);
    } else {
      cpnet.page = [cpnet.page, page];
    }

    this.updateInstances();
  }

  deleteElementInBlock(block, elementType, id) {
    // blcok[elementType] = blcok[elementType].filter(elem => elem._id !== id);
    if (!(block[elementType] instanceof Array)) {
      block[elementType] = [block[elementType]];
    }
    for (let i = 0; i < block[elementType].length; i++) {
      if (block[elementType][i]._id === id) {
        block[elementType].splice(i, 1);
        if (block[elementType].length === 0) {
          delete block[elementType];
        }
      }
    }
  }

  deleteMonitorInBlock(_block, _id) { }

  /**
   * Getters for new cpnElement
   */

  /**
   * Creating empty page cpnElement
   * @param name - name of new page
   * @returns - new page cpnElement
   */
  createCpnPage(name, id = undefined) {
    // const newPage = {
    //   pageattr: {
    //     _name: name
    //   },
    //   constraints: '',
    //   _id: id ? id : getNextId()
    // };

    const placeList = this.getAllPlaces();
    const pageList = this.getAllPages();

    const newPage = cloneObject(DEFAULT_PAGE);
    newPage.pageattr._name = name + " " + (pageList.length + 1);
    newPage._id = id || getNextId();

    // newPage.place._id = getNextId();
    // newPage.place.text = "P" + (placeList.length + 1);

    // this.updateInstances();
    return newPage;
  }

  /**
   * Creating empty block cpnElement
   * @param name - name of new block
   * @returns - new block cpnElement
   */
  createCpnBlock(name) {
    return {
      id: name,
      _id: getNextId(),
    };
  }

  /**
   * Creating empty declaration cpnElement
   * @param layout - value of new declaration
   * @returns - new declaration cpnElement
   */
  createCpnDeclaration(layout) {
    return {
      layout: layout,
      _id: getNextId(),
    };
  }

  // ------------------------------------------------
  // DC: 'Data collection',
  // MS: 'Marking size',
  // BP: 'Break point',
  // UD: 'User defined',
  // WIF: 'Write in file',
  // LLDC: 'List length data collection',
  // CTODC: 'Count transition occurence data collector',
  // PCBP: 'Place content break point',
  // TEBP: 'Transition enabled break point'
  // ------------------------------------------------

  getShapeNames(cpnElement) {
    let s = "";
    for (const e of nodeToArray(cpnElement)) {
      if (s.length > 0) {
        s += ", ";
      }
      s += e.text;
    }
    return s;
  }

  getMonitorNodeList(cpnElement) {
    const nodeList = [];

    for (const e of nodeToArray(cpnElement)) {
      const nodePage = this.getPageByElementId(e._id);
      const inst = this.getInstance(nodePage._id);

      nodeList.push({
        _idref: e._id,
        _pageinstanceidref: inst ? inst._id : "",
      });
    }
    if (nodeList.length === 0) {
      return undefined;
    }
    return nodeList.length === 1 ? nodeList[0] : nodeList;
  }

  createCpnMonitor(monitorType: string, cpnElementList, monitorDefaults) {
    // monitorDefaults
    // {
    //   "defaultInit": "string",
    //   "defaultObserver": "string",
    //   "defaultPredicate": "string",
    //   "defaultStop": "string",
    //   "defaultTimed": true
    // }

    let newMonitorCpnElement;
    switch (monitorType) {
      case this.monitorType.BP:
        newMonitorCpnElement = this.createCpnMonitorBP(
          cpnElementList,
          monitorDefaults
        );
        break;
      case this.monitorType.CTODC:
        newMonitorCpnElement = this.createMonitorCTODC(
          cpnElementList,
          monitorDefaults
        );
        break;
      case this.monitorType.DC:
        newMonitorCpnElement = this.createCpnMonitorDC(
          cpnElementList,
          monitorDefaults
        );
        break;
      case this.monitorType.LLDC:
        newMonitorCpnElement = this.createCpnMonitorLLDC(
          cpnElementList,
          monitorDefaults
        );
        break;
      case this.monitorType.MS:
        newMonitorCpnElement = this.createCpnMonitorMS(
          cpnElementList,
          monitorDefaults
        );
        break;
      case this.monitorType.PCBP:
        newMonitorCpnElement = this.createCpnMonitorPCBP(
          cpnElementList,
          monitorDefaults
        );
        break;
      case this.monitorType.TEBP:
        newMonitorCpnElement = this.createCpnMonitorTEBP(
          cpnElementList,
          monitorDefaults
        );
        break;
      case this.monitorType.UD:
        newMonitorCpnElement = this.createCpnMonitorUD(
          cpnElementList,
          monitorDefaults
        );
        break;
      case this.monitorType.WIF:
        newMonitorCpnElement = this.createCpnMonitorWIF(
          cpnElementList,
          monitorDefaults
        );
        break;
    }

    // this.updateInstances();

    return newMonitorCpnElement;
  }

  getMonitorDefaults(
    monitorDefaults,
    monitorTemplate: DataCollectionMonitorTemplate
  ) {
    return {
      defaultInit:
        monitorDefaults && monitorDefaults.defaultInit
          ? monitorDefaults.defaultInit
          : monitorTemplate.defaultInit(),
      defaultObserver:
        monitorDefaults && monitorDefaults.defaultObserver
          ? monitorDefaults.defaultObserver
          : monitorTemplate.defaultObserver(),
      defaultPredicate:
        monitorDefaults && monitorDefaults.defaultPredicate
          ? monitorDefaults.defaultPredicate
          : monitorTemplate.defaultPredicate(),
      defaultStop:
        monitorDefaults && monitorDefaults.defaultStop
          ? monitorDefaults.defaultStop
          : monitorTemplate.defaultStop(),
      defaultTimed:
        monitorDefaults && monitorDefaults.defaultTimed
          ? monitorDefaults.defaultTimed
          : monitorTemplate.defaultTimed(),
      defaultLogging:
        monitorDefaults && monitorDefaults.defaultLogging
          ? monitorDefaults.defaultLogging
          : monitorTemplate.defaultLogging(),
    };
  }

  createCpnMonitorDC(cpnElement, monitorDefaults) {
    console.log("createCpnMonitorDC(), cpnElement = ", cpnElement);
    const monitorTemplate = new DataCollectionMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name: monitorTemplate.typeDescription() + " monitor",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      declaration: [
        {
          _name: "Predicate",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultPredicate },
        },
        {
          _name: "Observer",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultObserver },
        },
        {
          _name: "Init function",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultInit },
        },
        {
          _name: "Stop",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultStop },
        },
      ],
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  createCpnMonitorBP(cpnElement, monitorDefaults) {
    console.log("createCpnMonitorBP(), cpnElement = ", cpnElement);
    const monitorTemplate = new BreakpointMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name: monitorTemplate.typeDescription() + " monitor",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      declaration: [
        {
          _name: "Predicate",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultPredicate },
        },
        {
          _name: "Observer",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultObserver },
        },
        {
          _name: "Init function",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultInit },
        },
        {
          _name: "Stop",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultStop },
        },
      ],
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  createCpnMonitorUD(cpnElement, monitorDefaults) {
    console.log("createCpnMonitorUD(), cpnElement = ", cpnElement);
    const monitorTemplate = new UserDefinedMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name: monitorTemplate.typeDescription() + " monitor",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      declaration: [
        {
          _name: "Predicate",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultPredicate },
        },
        {
          _name: "Observer",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultObserver },
        },
        {
          _name: "Init function",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultInit },
        },
        {
          _name: "Stop",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultStop },
        },
      ],
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  createCpnMonitorWIF(cpnElement, monitorDefaults) {
    console.log("createCpnMonitorWIF(), cpnElement = ", cpnElement);
    const monitorTemplate = new WriteInFileMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name: monitorTemplate.typeDescription() + " monitor",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      declaration: [
        {
          _name: "Predicate",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultPredicate },
        },
        {
          _name: "Observer",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultObserver },
        },
        {
          _name: "Init function",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultInit },
        },
        {
          _name: "Stop",
          ml: { _id: getNextId(), __text: monitorDefaults.defaultStop },
        },
      ],
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  createCpnMonitorMS(cpnElement, monitorDefaults) {
    console.log("createCpnMonitorMS(), cpnElement = ", cpnElement);
    const monitorTemplate = new MarkingSizeMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name:
        monitorTemplate.typeDescription() +
        " monitor (" +
        this.getShapeNames(cpnElement) +
        ")",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  createCpnMonitorLLDC(cpnElement, monitorDefaults) {
    console.log("createCpnMonitorLLDC(), cpnElement = ", cpnElement);
    const monitorTemplate = new ListLengthDataCollectionMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name:
        monitorTemplate.typeDescription() +
        " monitor (" +
        this.getShapeNames(cpnElement) +
        ")",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  createMonitorCTODC(cpnElement, monitorDefaults): any {
    console.log("createMonitorCTODC(), cpnElement = ", cpnElement);
    const monitorTemplate = new CountTransitionOccurrencesMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name:
        monitorTemplate.typeDescription() +
        " monitor (" +
        this.getShapeNames(cpnElement) +
        ")",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  createCpnMonitorPCBP(cpnElement, monitorDefaults) {
    console.log("createCpnMonitorPCBP(), cpnElement = ", cpnElement);
    const monitorTemplate = new PlaceContentBreakPointMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name:
        monitorTemplate.typeDescription() +
        " monitor (" +
        this.getShapeNames(cpnElement) +
        ")",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  createCpnMonitorTEBP(cpnElement, monitorDefaults) {
    console.log("createCpnMonitorTEBP(), cpnElement = ", cpnElement);
    const monitorTemplate = new TransitionEnabledBreakPointMonitorTemplate();

    monitorDefaults = this.getMonitorDefaults(monitorDefaults, monitorTemplate);

    return {
      _id: getNextId(),
      _name:
        monitorTemplate.typeDescription() +
        " monitor (" +
        this.getShapeNames(cpnElement) +
        ")",
      _type: monitorTemplate.type(),
      _typedescription: monitorTemplate.typeDescription(),
      _disabled: "false",
      node: this.getMonitorNodeList(cpnElement),
      option: [
        { _name: "Timed", _value: monitorDefaults.defaultTimed },
        { _name: "Logging", _value: monitorDefaults.defaultLogging },
      ],
    };
  }

  /**
   * Convert cpn declaration element to string
   * @param cpnElement - declaration element
   */
  cpnDeclarationToString(cpnDeclarationType, cpnElement) {
    let layout = "";

    switch (cpnDeclarationType) {
      case "globref":
        layout = this.cpnGlobrefToString(cpnElement);
        break;
      case "color":
        layout = this.cpnColorToString(cpnElement);
        break;
      case "var":
        layout = this.cpnVarToString(cpnElement);
        break;
      case "ml":
        layout = this.cpnMlToString(cpnElement);
        break;
      case "monitor":
        layout = this.cpnMonitorToString(cpnElement);
        break;
      default:
        return cpnElement.layout || JSON.stringify(cpnElement);
    }

    return layout;
  }

  /**
   * Convert cpn globref element to string
   * @param cpnElement - color(colset) cpn element
   */
  cpnGlobrefToString(cpnElement) {
    const str = "globref " + cpnElement.id + " = " + cpnElement.ml + ";";
    return str;
  }

  /**
   * Convert cpn color(colset) element to string
   * @param cpnElement - color(colset) cpn element
   */
  cpnColorToString(cpnElement) {
    let str = "colset " + cpnElement.id;

    const color = cpnElement;
    if (color.layout) {
      str = color.layout;
    } else {
      if (color.alias && color.alias.id) {
        str += " = " + color.alias.id;
      } else if (color.list && color.list.id) {
        str += " = list " + color.list.id;
      } else if (color.product && color.product.id) {
        str += " = product ";
        if (color.product.id instanceof Array) {
          for (let i = 0; i < color.product.id.length; i++) {
            str += i === 0 ? color.product.id[i] : " * " + color.product.id[i];
          }
        } else {
          str += color.product.id;
        }
      } else {
        str += " = " + color.id.toLowerCase();
      }
      if ("timed" in color) {
        str += " timed";
      }
      str += ";";
    }

    return str;
  }

  /**
   * Convert cpn var element to string
   * @param cpnElement - var cpn element
   */
  cpnVarToString(cpnElement) {
    let str = "var " + cpnElement.id;

    if (cpnElement.layout) {
      str = cpnElement.layout;
    } else {
      str = "var " + cpnElement.id + ": " + cpnElement.type.id + ";";
    }

    return str;
  }

  /**
   * Convert cpn globref element to string
   * @param cpnElement - color(colset) cpn element
   */
  cpnMlToString(cpnElement) {
    const str = cpnElement.__text || cpnElement.layout;
    return str;
  }

  /**
   * Convert cpn declaration element to string
   * @param cpnElement
   * @param type
   */
  cpnDeclarationElementToString(cpnElement, type) {
    switch (type) {
      case "globref":
        return this.cpnGlobrefToString(cpnElement);
      case "color":
        return this.cpnColorToString(cpnElement);
      case "var":
        return this.cpnVarToString(cpnElement);
      case "ml":
        return this.cpnMlToString(cpnElement);
    }
  }

  private cpnMonitorToString(cpnElement: any): string {
    return cpnElement._name;
  }

  /**
   * Parse declaration type from string
   */
  parseDeclarationTypeFromString(str) {
    const parser = str.match("^\\S+");
    let declarationType = "";

    if (parser) {
      declarationType = parser[0];
    }

    return ["var", "colset", "globref", "ml", "val", "fun", "local"].includes(
      declarationType
    )
      ? declarationType
      : "ml";
  }

  trimChars(str, c) {
    str = str.trim();
    var re = new RegExp("^[" + c + "]+|[" + c + "]+$", "g");
    return str.replace(re, "");
  }

  /**
   * Convert string to cpn declaration element
   * @param cpnElement
   * @param str
   */
  stringToCpnDeclarationElement(cpnElement, str) {
    let resultCpnType = "ml";
    let resultCpnElement: any = { _id: cpnElement._id };

    // const parser = str.match('^\\S+');
    // console.log('stringToCpnDeclarationElement(), parser = ', parser);

    // str = str.trim();
    // str = this.trimChars(str, ';');

    let resultDeclarationType = this.parseDeclarationTypeFromString(str);
    if (!resultDeclarationType) {
      resultDeclarationType = "ml";
    }

    switch (resultDeclarationType) {
      case "var":
        resultCpnType = "var";
        let splitLayoutArray;
        resultCpnElement.layout = str;

        str = this.trimChars(str, ";");
        str = str.replace("var", "");
        splitLayoutArray = str.trim().split(":");
        for (let i = 0; i < splitLayoutArray.length; i++) {
          splitLayoutArray[i] = splitLayoutArray[i]
            .replace(/\s+/g, "")
            .split(",");
        }
        resultCpnElement.id = splitLayoutArray[0];
        if (!resultCpnElement.type) {
          resultCpnElement.type = {};
        }
        if (splitLayoutArray[1]) {
          resultCpnElement.type.id = splitLayoutArray[1][0];
        }
        break;

      case "colset": // TODO: отрефакторить
        resultCpnType = "color";
        // cpnElement.layout = str;
        str = str.replace("colset", "");
        splitLayoutArray = str.split("=");

        if (splitLayoutArray[1]) {
          splitLayoutArray[1] = splitLayoutArray[1]
            .split(" ")
            .filter((e) => e.trim() !== "");
          let testElem = splitLayoutArray[1][0].replace(/\s+/g, "");
          for (const key of Object.keys(resultCpnElement)) {
            if (key !== "_id" && key !== "layout") {
              delete resultCpnElement[key];
            }
          }
          if (
            splitLayoutArray[1][splitLayoutArray[1].length - 1].replace(
              ";",
              ""
            ) === "timed"
          ) {
            resultCpnElement.timed = "";
            splitLayoutArray[1].length = splitLayoutArray[1].length - 1;
          }
          if (testElem === "product") {
            const productList = splitLayoutArray[1]
              .slice(1)
              .filter((e) => e.trim() !== "*");
            for (const i in productList) {
              productList[i] = this.trimChars(productList[i], ";");
              productList[i] = this.trimChars(productList[i], "*");
            }

            resultCpnElement.id = splitLayoutArray[0].replace(/\s+/g, "");
            resultCpnElement.product = {
              id: productList.length === 1 ? productList[0] : productList,
            };
          } else if (testElem === "list") {
            const productList = splitLayoutArray[1]
              .slice(1)
              .filter((e) => e.trim() !== "*");
            for (const i in productList) {
              productList[i] = this.trimChars(productList[i], ";");
              productList[i] = this.trimChars(productList[i], "*");
            }

            resultCpnElement.id = splitLayoutArray[0].replace(/\s+/g, "");
            resultCpnElement.list = {
              id: productList.length === 1 ? productList[0] : productList,
            };
          } else {
            testElem = testElem.replace(/\s+/g, "").replace(";", "");
            splitLayoutArray[1][0] = splitLayoutArray[1][0]
              .replace(/\s+/g, "")
              .replace(";", "");

            // console.log('stringToCpnDeclarationElement(), testElem = ', testElem);
            // console.log('stringToCpnDeclarationElement(), splitLayoutArray = ', splitLayoutArray);

            if (
              testElem.toLowerCase() === splitLayoutArray[1][0].toLowerCase()
            ) {
              resultCpnElement.id = splitLayoutArray[0].trim();
              resultCpnElement[testElem.toLowerCase()] = "";
            } else {
              resultCpnElement.id = splitLayoutArray[0].trim();
              resultCpnElement.alias = { id: testElem };
            }
          }
        }
        break;

      case "globref":
        resultCpnType = "globref";
        splitLayoutArray = str
          .split(" ")
          .filter((e) => e.trim() !== "" && e.trim() !== "=");
        resultCpnElement.id = splitLayoutArray[1]
          .replace(/\s+/g, "")
          .replace(";", "");
        resultCpnElement.ml = splitLayoutArray[2]
          .replace(/\s+/g, "")
          .replace(";", "");
        // cpnElement.layout = str;
        break;

      // case 'ml':
      // case 'val':
      // case 'fun':
      // case 'local':
      default:
        resultCpnType = "ml";
        resultDeclarationType = "ml";
        resultCpnElement.layout = str;
        resultCpnElement.__text = str;
        break;
    }

    // console.log('stringToCpnDeclarationElement(), cpnType = ', cpnType);
    // console.log('stringToCpnDeclarationElement(), declarationType = ', declarationType);
    // console.log('stringToCpnDeclarationElement(), cpnElement = ', cpnElement);

    return {
      cpnType: resultCpnType,
      declarationType: resultDeclarationType,
      cpnElement: resultCpnElement,
    };
  }

  updateDeclaration(
    declarationCpnElement,
    declarationCpnType,
    parentBlockCpnElement,
    layout
  ) {
    const originalLayout = layout;

    console.log("updateDeclaration(), layout = ", layout);
    console.log(
      "updateDeclaration(), declarationCpnElement = ",
      JSON.stringify(declarationCpnElement)
    );

    const oldCpnDeclarartionType = declarationCpnType;

    // parse declaration layout
    let result = parseDeclarartion(layout);

    if (result && result.cpnElement) {
      let newDeclaration = result.cpnElement;
      const newCpnDeclarartionType = result.cpnDeclarationType;

      console.log(
        "onUpdate(), oldCpnDeclarartionType = ",
        oldCpnDeclarartionType
      );
      console.log(
        "onUpdate(), newCpnDeclarartionType = ",
        newCpnDeclarartionType
      );

      this.copyDeclaration(newDeclaration, declarationCpnElement);

      // move declaration cpn element from old declaration group to new, if needed
      if (newCpnDeclarartionType !== oldCpnDeclarartionType) {
        this.removeCpnElement(
          parentBlockCpnElement,
          declarationCpnElement,
          oldCpnDeclarartionType
        );
        this.addCpnElement(
          parentBlockCpnElement,
          declarationCpnElement,
          newCpnDeclarartionType
        );
      }
    }
  }

  copyDeclaration(fromDeclaration, toDeclaration) {
    for (const key in toDeclaration) {
      if (key !== "_id") {
        delete toDeclaration[key];
      }
    }
    for (const key in fromDeclaration) {
      if (key !== "_id") {
        toDeclaration[key] = fromDeclaration[key];
      }
    }
  }

  /**
   * Get all pages list
   */
  getAllPages() {
    const cpn = this.getCpn();
    if (!cpn) {
      return [];
    }
    const page = this.getCpn().page;
    if (!page) {
      return [];
    }
    return page instanceof Array ? page : [page];
  }

  getRootPages() {
    const subpageIdList = this.getSubPageIds();
    return this.getAllPages().filter(
      (p) => p && !subpageIdList.includes(p._id)
    );
  }

  getSubPageIds() {
    const pageIdList = [];
    for (const t of this.getAllTrans()) {
      if (t && t.subst && t.subst._subpage) {
        pageIdList.push(t.subst._subpage);
      }
    }
    return pageIdList;
  }

  /**
   * Get page object from model by id
   * @param pageId
   */
  public getPageById(pageId) {
    return this.getAllPages().find((page) => page && page._id === pageId);
  }

  /**
   * Checks if page is subpage
   * @param pageId - page id
   */
  public isSubpage(pageId) {
    const transList = this.getAllTrans().filter((trans) => {
      return trans.subst && trans.subst._subpage === pageId;
    });
    // console.log(this.constructor.name, 'isSubpage(), pageId, transList = ', pageId, transList)
    return transList && transList.length > 0 ? true : false;
  }

  /**
   * Find page by place or transitions id
   * @param id - place or transition id
   */
  public getPageByElementId(id) {
    const pages = this.getAllPages();

    for (const page of pages) {
      // search in transitions
      for (const t of nodeToArray(page.trans)) {
        if (t._id === id) {
          return page;
        }
      }
      // search in places
      for (const p of nodeToArray(page.place)) {
        if (p._id === id) {
          return page;
        }
      }
      // search in arcs
      for (const a of nodeToArray(page.arc)) {
        if (a._id === id) {
          return page;
        }
      }
    }

    return undefined;
  }

  /**
   * Find place or transitions by id
   * @param id - place or transition id
   */
  public getPlaceOrTransitionById(id) {
    const pages = this.getAllPages();

    for (const page of pages) {
      // search in transitions
      for (const t of nodeToArray(page.trans)) {
        if (t._id === id) {
          return { element: t, type: "Transition" };
        }
      }
      // search in places
      for (const p of nodeToArray(page.place)) {
        if (p._id === id) {
          return { element: p, type: "Place" };
        }
      }
    }

    return undefined;
  }

  public getTransById(id) {
    const pages = this.getAllPages();

    for (const page of pages) {
      // search in trans
      for (const trans of nodeToArray(page.trans)) {
        if (trans._id === id) {
          return trans;
        }
      }
    }

    return undefined;
  }

  public getTransListBySubpageId(id) {
    const result = [];

    const pages = this.getAllPages();

    for (const page of pages) {
      // search in trans
      for (const trans of nodeToArray(page.trans)) {
        if (trans.subst && trans.subst._subpage === id) {
          // return trans;
          result.push(trans);
        }
      }
    }

    return result;
  }

  public getArcById(id) {
    const pages = this.getAllPages();

    for (const page of pages) {
      // search in arcs
      for (const arc of nodeToArray(page.arc)) {
        if (arc._id === id) {
          return arc;
        }
      }
    }

    return undefined;
  }

  public getTransitionIncomeArcs(transId) {
    const arcs = [];
    const pages = this.getAllPages();
    for (const page of pages) {
      // search in arcs
      for (const arc of nodeToArray(page.arc)) {
        if (
          arc.transend._idref === transId &&
          ["PtoT", "BOTHDIR"].includes(arc._orientation)
        ) {
          arcs.push(arc);
        }
      }
    }
    return arcs;
  }

  public getTransitionOutcomeArcs(transId, placeId?) {
    const arcs = [];
    const pages = this.getAllPages();
    for (const page of pages) {
      // search in arcs
      for (const arc of nodeToArray(page.arc)) {
        if (
          arc.transend._idref === transId &&
          (placeId === undefined || arc.placeend._idref === placeId) &&
          ["TtoP", "BOTHDIR"].includes(arc._orientation)
        ) {
          arcs.push(arc);
        }
      }
    }
    return arcs;
  }

  /**
   * Get page id by name
   * @param pageName
   */
  getPageId(pageName) {
    const pageList = this.getAllPages();
    for (const p of pageList) {
      if (p.pageattr._name === pageName) {
        return p._id;
      }
    }
    return undefined;
  }

  /**
   * Get all places for model
   */
  getAllPlaces() {
    const allPlaces = [];
    const allPages = this.getAllPages();
    if (allPages && allPages.length > 0) {
      for (const page of allPages) {
        if (page) {
          const places = nodeToArray(page.place);
          for (const p of places) {
            if (p) {
              allPlaces.push(p);
            }
          }
        }
      }
    }

    return allPlaces;
  }

  /**
   * Get all trans for model
   */
  getAllTrans() {
    const allTrans = [];
    const allPages = this.getAllPages();
    if (allPages && allPages.length > 0) {
      for (const page of allPages) {
        if (page) {
          const trans = page.trans instanceof Array ? page.trans : [page.trans];
          for (const t of trans) {
            if (t) {
              allTrans.push(t);
            }
          }
        }
      }
    }

    return allTrans;
  }

  /**
   * Get all acrs for model
   */
  getAllArcs() {
    const allArcs = [];

    for (const page of this.getAllPages()) {
      const arcs = page.arc instanceof Array ? page.arc : [page.arc];
      for (const a of arcs) {
        if (a) {
          allArcs.push(a);
        }
      }
    }


    return allArcs;
  }

  /**
   * Get all blocks
   */
  getAllBlocks() {
    const cpn = this.getCpn();
    if (!cpn) {
      return [];
    }

    let allBlocks = [];

    nodeToArray(cpn.globbox.block).forEach((b) => {
      allBlocks.push(b);
      nodeToArray(b.block).forEach((b2) => {
        allBlocks.push(b2);
        nodeToArray(b2.block).forEach((b3) => {
          allBlocks.push(b3);
        });
      });
    });

    return allBlocks;
  }

  /**
   * Get all arcs, wich are connecting elements
   * @param cpnElements - array of elements
   */
  getArcsForElements(cpnElements) {
    const cpnElementIds = [];
    for (const e of cpnElements) {
      if (e._id) {
        cpnElementIds.push(e._id);
      }
    }
    if (cpnElementIds.length < 1) {
      return;
    }

    const arcs = [];
    for (const arc of this.getAllArcs()) {
      if (arc) {
        if (
          cpnElementIds.includes(arc.placeend._idref) &&
          cpnElementIds.includes(arc.transend._idref)
        ) {
          arcs.push(arc);
        }
      }
    }
    return arcs;
  }

  /**
   * Get all arcs, wich are connecting with elements but not between them
   * @param cpnElements - array of elements
   */
  getExternalArcsForElements(cpnElements) {
    const cpnElementIds = [];
    for (const e of cpnElements) {
      if (e._id) {
        cpnElementIds.push(e._id);
      }
    }
    if (cpnElementIds.length < 1) {
      return;
    }

    const arcs = [];
    for (const arc of this.getAllArcs()) {
      if (arc) {
        if (
          (cpnElementIds.includes(arc.placeend._idref) &&
            !cpnElementIds.includes(arc.transend._idref)) ||
          (!cpnElementIds.includes(arc.placeend._idref) &&
            cpnElementIds.includes(arc.transend._idref))
        ) {
          arcs.push(arc);
        }
      }
    }
    return arcs;
  }

  /**
   * Move elements from page to page
   */
  moveElements(fromPageId, toPageId, elements) {
    const fromPage = this.getPageById(fromPageId);
    const toPage = this.getPageById(toPageId);

    if (!fromPage || !toPage) {
      return;
    }

    for (const element of elements) {
      // place element
      if (element.ellipse) {
        // remove place from old page
        this.removeCpnElement(fromPage, element, "place");
        // add place to new page
        this.addCpnElement(toPage, element, "place");
      }

      // transition element
      else if (element.box) {
        // remove trans from old page
        this.removeCpnElement(fromPage, element, "trans");
        // add trans to new page
        this.addCpnElement(toPage, element, "trans");
      }

      // arc element
      else if (element.transend) {
        // remove arc from old page
        this.removeCpnElement(fromPage, element, "arc");
        // add arc to new page
        this.addCpnElement(toPage, element, "arc");
      }
    }
  }

  /**
   * Get next page name
   */
  getNextPageName(pageName) {
    let n = 1;
    let newPageName = pageName
      ? pageName
      : this.settings.appSettings["page"] + " " + n;

    for (const page of this.getAllPages()) {
      if (newPageName === page.pageattr._name) {
        if (newPageName.includes(n.toString())) {
          newPageName = newPageName.replace(n.toString(), (++n).toString());
        } else {
          newPageName = newPageName + " " + n;
        }
      }
    }

    return newPageName;
  }

  /**
   * Create subpage for transition
   * @param transCpnElement
   * @param newPageName
   * @param newPageId
   */
  createSubpage(transCpnElement, newPageName, newPageId) {
    const pageName = this.getNextPageName(newPageName);
    const pageId = newPageId ? newPageId : getNextId();

    const subpageCpnElement = this.createCpnPage(pageName, pageId);
    transCpnElement.subst.subpageinfo._name = subpageCpnElement.pageattr._name;
    transCpnElement.subst._subpage = subpageCpnElement._id;

    this.createNewPage(subpageCpnElement);

    return subpageCpnElement;
  }

  /**
   * Create port object for place
   * @param cpnElement
   * @param portType
   */
  createPortObject(cpnElement, portType) {
    if (!cpnElement || !cpnElement.ellipse) {
      return undefined;
    }

    const x = Number(cpnElement.posattr._x);
    const y = Number(cpnElement.posattr._y);
    const w = Number(cpnElement.ellipse._w);
    const h = Number(cpnElement.ellipse._h);

    return {
      fillattr: { _colour: "White", _pattern: "Solid", _filled: "false" },
      lineattr: { _colour: "Black", _thick: "0", _type: "Solid" },
      posattr: { _x: x.toString(), _y: (y - h / 2).toString() },
      textattr: { _colour: "Black", _bold: "false" },
      text: portType,
      _id: cpnElement._id + "e",
      _type: portType === "In/Out" ? "I/O" : portType,
    };
  }

  /**
   * Create subst object for transition
   * @param cpnElement
   * @param name
   * @param pageId
   */
  createSubstObject(cpnElement, name, pageId) {
    if (!cpnElement || !cpnElement.box) {
      return undefined;
    }

    const x = Number(cpnElement.posattr._x);
    const y = Number(cpnElement.posattr._y);
    const w = Number(cpnElement.box._w);
    const h = Number(cpnElement.box._h);

    return {
      subpageinfo: {
        fillattr: { _colour: "White", _pattern: "Solid", _filled: "false" },
        lineattr: { _colour: "Black", _thick: "0", _type: "Solid" },
        posattr: { _x: x.toString(), _y: (y - h / 2).toString() },
        textattr: { _colour: "Black", _bold: "false" },
        _id: cpnElement._id + "e",
        _name: name,
      },
      _portsock: "",
      _subpage: pageId,
    };
  }

  /**
   * Find end place and transition for arc
   */
  getArcEnds(cpnElement) {
    const allPlaces = this.getAllPlaces();
    const allTrans = this.getAllTrans();

    const placeEnd = allPlaces.find(
      (p) => p._id === cpnElement.placeend._idref
    );
    const transEnd = allTrans.find((t) => t._id === cpnElement.transend._idref);

    return {
      place: placeEnd,
      trans: transEnd,
      orient: cpnElement._orientation,
    };
  }

  /**
   * Getting all port places for transition
   * @param cpnElement
   * @param transEnd
   */
  getAllPorts(cpnElement, transEnd) {
    const ports = [];
    // console.log('getAllPorts(), transEnd = ', transEnd);

    if (transEnd.subst) {
      const page = this.getPageById(transEnd.subst._subpage);
      if (page && page.place) {
        // console.log('getAllPorts(), page = ', page);

        for (const place of nodeToArray(page.place)) {
          // console.log('getAllPorts(), place = ', place);
          // console.log('getAllPorts(), place.port = ', place.port);
          if (
            place.port &&
            (place.port._type === "I/O" ||
              place.port._type ===
              (cpnElement._orientation === "TtoP" ? "Out" : "In"))
          ) {
            ports.push(place);
          }
        }
      }
    }
    return ports;
  }

  /**
   * Get place name by id
   */
  getPortNameById(pageId, id) {
    const page = this.getPageById(pageId);
    if (page) {
      const port = page.place.find((e) => e._id === id);
      return port.text;
    }
  }

  /**
   * Get port place id by name
   * @param pageId
   * @param text
   * @param orient
   */
  getPortIdByName(pageId, text, orient) {
    const page = this.getPageById(pageId);
    if (page && text !== "") {
      const port = page.place.find(
        (e) =>
          e.text === text &&
          (e.port._type === "I/O" ||
            e.port._type === (orient === "TtoP" ? "Out" : "In"))
      );
      return port._id;
    } else {
      return undefined;
    }
  }

  /**
   * Create new declaration
   *
   * @param parentBlock
   * @param declaration
   * @param declarationType
   */
  newDeclaration(parentBlock, declarationType, insertAfterElement?) {
    if (parentBlock && declarationType) {
      let parentCpnElement = parentBlock;

      let newLayout = this.settings.appSettings["declaration"];
      switch (declarationType) {
        case "globref":
          newLayout = this.settings.appSettings["globref"];
          break;
        case "color":
          newLayout = this.settings.appSettings["color"];
          break;
        case "var":
          newLayout = this.settings.appSettings["var"];
          break;
        case "fun":
          newLayout = this.settings.appSettings["fun"];
          break;
      }

      if (newLayout) {
        // parse declaration layout
        let result = parseDeclarartion(newLayout);
        // console.log(this.constructor.name, 'onNewDeclaration(), result = ', result);

        if (result && result.cpnElement) {
          let newDeclaration = result.cpnElement;
          let newCpnDeclarartionType = result.cpnDeclarationType;

          // set new id value
          newDeclaration._id = getNextId();

          // add declaration cpn element to declaration group
          this.addCpnElement(
            parentCpnElement,
            newDeclaration,
            newCpnDeclarartionType,
            insertAfterElement
          );

          return newDeclaration;
        }
      }
    }
    return undefined;
  }

  /**
   * Add cpn element to parent
   * @param cpnParentElement
   * @param cpnElement
   * @param cpnType - new cpn type where cpn element should be placed
   */
  addCpnElement(
    cpnParentElement,
    cpnElement,
    cpnType,
    insertAfterElement = undefined,
    insertFirst = false
  ) {
    try {
      if (!cpnParentElement) {
        throw "Undefined cpnParentElement element";
      }
      if (!cpnElement) {
        throw "Undefined cpnElement element";
      }
      if (!cpnType) {
        throw "Undefined cpnType";
      }

      const nodeList = nodeToArray(cpnParentElement[cpnType]);
      if (insertAfterElement) {
        const find = nodeList.indexOf(insertAfterElement);
        if (find > -1) {
          nodeList.splice(find + 1, 0, cpnElement);
        } else {
          console.error("insertAfterElement not found");
        }
      } else {
        insertFirst ? nodeList.unshift(cpnElement) : nodeList.push(cpnElement);
      }
      cpnParentElement[cpnType] =
        nodeList.length === 1 ? nodeList[0] : nodeList;

      // console.log('addCpnElement(), cpnType = ', cpnType);
      // console.log('addCpnElement(), cpnParentElement = ', cpnParentElement);
      if (cpnType === "block") {
        this.fixBlockOrder();
      }
    } catch (ex) {
      console.error(this.constructor.name, "addCpnElement(). ERROR: ", ex);
    }

    console.log(
      this.constructor.name,
      "addCpnElement(), cpnParentElement, cpnElement = ",
      cpnParentElement,
      cpnElement
    );
  }

  fixBlockOrder() {
    const cpn = this.getCpn();
    if (cpn && cpn.globbox) {
      const blocks = nodeToArray(cpn.globbox.block);
      blocks.forEach((block, i) => {
        if (block.block) {
          const newBlock = { id: block.id, block: block.block, ...block };
          blocks[i] = newBlock;
        }
      });
    }
  }

  /**
   * Update cpn element in it's parent
   * @param cpnParentElement
   * @param cpnElement
   * @param cpnType - new cpn type where cpn element should be placed
   */
  updateCpnElement(cpnParentElement, cpnElement, cpnType) {
    try {
      if (!cpnParentElement) {
        throw "Undefined cpnParentElement element";
      }
      if (!cpnElement) {
        throw "Undefined cpnElement element";
      }
      if (!cpnType) {
        throw "Undefined cpnType";
      }

      if (cpnParentElement[cpnType] instanceof Array) {
        for (let i = 0; i < cpnParentElement[cpnType].length; i++) {
          if (cpnParentElement[cpnType][i]._id === cpnElement._id) {
            cpnParentElement[cpnType][i] = cpnElement;
          }
        }
      } else {
        cpnParentElement[cpnType] = cpnElement;
      }
    } catch (ex) {
      console.error(this.constructor.name, "updateCpnElement(). ERROR: ", ex);
    }
  }

  /**
   * Remove cpn element from it's parent
   * @param cpnParentElement
   * @param cpnElement
   * @param cpnType - old cpn type from where cpn element should be removed
   */
  removeCpnElement(cpnParentElement, cpnElement, cpnType) {
    try {
      if (!cpnParentElement) {
        throw "Undefined cpnParentElement element";
      }
      if (!cpnElement) {
        throw "Undefined cpnElement element";
      }
      if (!cpnType) {
        throw "Undefined cpnType";
      }
      if (!cpnParentElement[cpnType]) {
        throw "Undefined cpnParentElement[cpnType] element";
      }

      let nodeList = nodeToArray(cpnParentElement[cpnType]);
      nodeList = nodeList.filter((e) => {
        return e._id !== cpnElement._id;
      });

      if (!nodeList || nodeList.length === 0) {
        delete cpnParentElement[cpnType];
      } else {
        cpnParentElement[cpnType] =
          nodeList.length === 1 ? nodeList[0] : nodeList;
      }
    } catch (ex) {
      console.error(this.constructor.name, "removeCpnElement(). ERROR: ", ex);
    }
  }

  /**
   * Move cpn element up or down in it's parent array
   * @param cpnParentElement
   * @param cpnElement
   * @param cpnType
   * @param direction - direction how to move: ['up','down']
   */
  moveCpnElement(cpnParentElement, cpnElement, cpnType, direction) {
    try {
      if (!cpnParentElement) {
        throw "Undefined cpnParentElement element";
      }
      if (!cpnElement) {
        throw "Undefined cpnElement element";
      }
      if (!cpnType) {
        throw "Undefined cpnType";
      }
      if (!cpnParentElement[cpnType]) {
        throw "Undefined cpnParentElement[cpnType] element";
      }
      if (!direction || !["up", "down"].includes(direction)) {
        throw 'Direction should be "up" or "down"';
      }

      let fromIndex = 0,
        nodeList = nodeToArray(cpnParentElement[cpnType]);

      for (let i = 0; i < nodeList.length; i++) {
        if (nodeList[i]._id === cpnElement._id) {
          fromIndex = i;
          break;
        }
      }
      let toIndex = undefined;
      switch (direction) {
        case "up":
          {
            toIndex = fromIndex - 1;
          }
          break;
        case "down":
          {
            toIndex = fromIndex + 1;
          }
          break;
      }
      console.log(
        this.constructor.name,
        "moveCpnElement(). fromIndex, toIndex = ",
        fromIndex,
        toIndex
      );
      if (toIndex !== undefined && toIndex >= 0 && toIndex < nodeList.length) {
        arrayMove(nodeList, fromIndex, toIndex);
      }

      cpnParentElement[cpnType] =
        nodeList.length === 1 ? nodeList[0] : nodeList;
    } catch (ex) {
      console.error(this.constructor.name, "moveUpCpnElement(). ERROR: ", ex);
    }
  }

  /**
   * Returns monitor node list with names of elements and pages
   * @param cpnElement = monitor cpn element
   */
  getMonitorNodeNamesList(cpnElement) {
    console.log("getMonitorNodeNamesList(), cpnElement = ", cpnElement);

    if (!cpnElement || !cpnElement.node) {
      return [];
    }

    const nodes = nodeToArray(cpnElement.node);

    const nodeList = [];
    for (const node of nodes) {
      const page = this.getPageByElementId(node._idref);
      const element = this.getPlaceOrTransitionById(node._idref);
      if (element) {
        nodeList.push({
          page: page,
          element: element.element,
          elementType: element.type,
          instanceId: node._pageinstanceidref,
        });
      }
    }
    return nodeList;
  }

  getStandardDeclarations() {
    const buffer = [];
    let result = [];

    const blockList = nodeToArray(
      this.projectData.workspaceElements.cpnet.globbox.block
    );

    blockList.forEach((el) => buffer.push(el));

    while (buffer.length > 0) {
      const pop = buffer.pop();
      if (pop.block) {
        if (Array.isArray(pop.block)) {
          pop.block.forEach((el) => buffer.push(el));
        } else {
          buffer.push(pop.block);
        }
      }
      if (pop.color) {
        if (Array.isArray(pop.color)) {
          result = result.concat(pop.color);
        } else {
          result.push(pop.color);
        }
      }
    }
    return result;
  }

  checkNumberOfMonitors(): number {
    let max = 0;
    const monitorsRoot = this.getMonitorsRoot();

    if (!monitorsRoot) {
      return;
    }

    const queue = [];
    const monitorBlocks = [];
    if (monitorsRoot.monitorblock) {
      nodeToArray(monitorsRoot.monitorblock).forEach((el) => queue.push(el));
    }
    while (queue.length > 0) {
      const item = queue.pop();
      if (item.monitorblock) {
        nodeToArray(item.monitorblock).forEach((el) => queue.push(el));
      }
      monitorBlocks.push(item._name);
    }
    monitorBlocks.forEach((block: string) => {
      const matches = block.match(" \\d*$");
      const idx = matches && matches.length > 0 ? +matches.pop().trim() : 0;
      max = idx > max ? idx : max;
    });
    return max;
  }

  fixShapeNames() {
    const places = this.getAllPlaces();
    const transitions = this.getAllTrans();

    const regex = /\s+\n/gim;

    places.forEach((p) => (p.text = p.text.replace(regex, "\n")));
    transitions.forEach((t) => (t.text = t.text.replace(regex, "\n")));
  }

  fixPlaceInitmark(place) {
    if (place && place.type && place.type.text) {
      const type = place.type.text.__text;
      if (type === "UNIT") {
        const prevVal = place.initmark.text.__text || "";
        if (prevVal !== "empty" && !prevVal.includes("`()")) {
          const val = +prevVal || 0;
          place.initmark.text.__text = val === 0 ? "" : val + "`()";
          return true;
        }
      }
    }
    return false;
  }

  fixArcAnnot(arc) {
    let place;

    if (arc.placeend) {
      const placeOrTransition = this.getPlaceOrTransitionById(
        arc.placeend._idref
      );
      place = placeOrTransition ? placeOrTransition.element : undefined;
    }

    if (place && place.type && place.type.text) {
      const type = place.type.text.__text;
      if (type === "UNIT") {
        const prevVal = arc.annot.text.__text || "";
        if (prevVal !== "empty" && !prevVal.includes("`()")) {
          const val = +prevVal || 0;
          arc.annot.text.__text = val === 0 ? "" : val + "`()";
          return true;
        }
      }
    }
    return false;
  }

  checkPaintElement() {
    const self = this;
    setTimeout(function () {
      const containerParent = document.getElementsByClassName("djs-container");
      let parentHeight = 577;
      for (let i = 0; i < containerParent.length; i++) {
        if (containerParent[i].clientHeight > 0) {
          parentHeight = containerParent[i].clientHeight;
          break;
        }
      }
      for (let i = 0; i < containerParent.length; i++) {
        const elements = containerParent[i].getElementsByTagName("svg");
        for (let k = 0; k < elements.length; k++) {
          elements[k]["style"].height = parentHeight + "px";
        }
      }
      // const elements = document.querySelectorAll('[data-element-id="__implicitroot"]');
      // const parentHeight = containerParent[containerParent.length - 1].clientHeight;
      // elements[0]['style'].height = parentHeight + 'px';
      // console.log('testQQQ');
    }, 1000);
  }

  doPlaceCaseId(caseId){
    this.placeCaseIdOnArcs(caseId);
    this.makePlacesContainIntegerTokens();
    this.updateStandardDeclarations(caseId);

    this.eventService.send(Message.PROJECT_LOAD, {
      project: this.project,
    });
  }

  setInitialMarking(nameBeginPlace, amountOfTokens){
    console.log(nameBeginPlace);
    console.log(amountOfTokens);
    const allPlaces = this.getAllPlaces();
    let beginPlace = allPlaces.find(element => element.text === nameBeginPlace);
    console.log(beginPlace);
    if(beginPlace == undefined){
      console.log("beginPlace is not defined")
    } else {
      beginPlace.initmark.text.__text = this.generateTokens(amountOfTokens);
      console.log(beginPlace);
      let location = beginPlace;
    }

    this.eventService.send(Message.PROJECT_LOAD, {
      project: this.project,
    });
  

    // let element = this._cpnFactory.createShape(
    //   undefined,
    //   transCpnElement,
    //   CPN_TRANSITION,
    //   position,
    //   true
    // );
  }

 

  //TODO let this work
  generateTokens(amount){
    let markingString = "";
    for(let i = 1; i <= amount; i++){
      markingString = markingString + "1`" + i + "++";
    }
    console.log(markingString);
    markingString = markingString.substring(0, markingString.length-2)
    return markingString;
  }

  placeCaseIdOnArcs(caseId){
    const allArcs = this.getAllArcs();
    for(const arc of allArcs){
      arc.annot.text = caseId;
    }
  }

  makePlacesContainIntegerTokens(){
    const allPlaces = this.getAllPlaces();
    for(const place of allPlaces){
      console.log(place.type.text);
      place.type.text = "TIMEDINT";
      console.log(place.type.text);
    }
  }

  updateStandardDeclarations(caseId){
    const standardDeclarationsBlock = this.getStandardDeclarationsBlock();
    console.log(standardDeclarationsBlock);
    this.addColorToStandardDeclarations(standardDeclarationsBlock, "TIMEDINT");
    this.addVarToStandardDeclarations(standardDeclarationsBlock, caseId);
  }

  addColorToStandardDeclarations(standardDeclarationsBlock, string){
    let hasColorInt = false;
    let hasColorTimedInt = false;
    nodeToArray(standardDeclarationsBlock.color).forEach((d) => {
      console.log(d);
      console.log(d.id);
      console.log(d.id.toLowerCase());
      console.log(d.id.toLowerCase() === "int");
      
      if(d.id.toLowerCase() === "int"){
        hasColorInt = true;
        console.log("Color INT has been found");
      }
      if(d.id.toLowerCase() === "timedint"){
        hasColorTimedInt = true;
        console.log("Color Timed INT has been found");
      }
    })

    // arcsForCopy.push({
    //   id: arc.id,
    //   cpnPlaceId: arc.cpnPlace._id,
    //   cpnTransitionId: arc.cpnTransition._id,
    //   orient: arc.cpnElement._orientation,
    //   label: arc.labels[0],
    // });

    let colorArray = nodeToArray(standardDeclarationsBlock.color);
    if(!hasColorInt){
      colorArray.push({
        _id:"ID4",
        id:"INT",
        int: "",
        layout: 'colset INT = int',
      })
    }
    if(!hasColorTimedInt){
      colorArray.push({
        _id:"ID12345",
        id: "TIMEDINT",
        int: "",
        layout: "colset TIMEDINT = int timed",
        timed: "",
      })
    }
    standardDeclarationsBlock.color = colorArray;
  }

  addVarToStandardDeclarations(standardDeclarationsBlock, caseId){
    let hasCaseIdVar = false;
    nodeToArray(standardDeclarationsBlock.var).forEach((v) => {
      console.log(v);
      console.log(v.id);
      console.log(v.id === caseId);
      if(v.id === caseId){
        hasCaseIdVar = true;
      }
    })
    if(!hasCaseIdVar){
      let standardDeclarationsArray = nodeToArray(standardDeclarationsBlock.var)
      console.log(standardDeclarationsBlock.color);
      console.log(standardDeclarationsBlock.var);
      standardDeclarationsArray.push({
        _id:"ID123456",
        id: caseId,
        layout: "var " + caseId + ": TIMEDINT",
        type: {id: 'TIMEDINT'},
      })
      standardDeclarationsBlock.var = standardDeclarationsArray;
    }
  }



  getStandardDeclarationsBlock(){
    let toReturn = undefined;
    const cpn = this.getCpn();
    if (!cpn) {
      return [];
    }

    nodeToArray(cpn.globbox.block).forEach((b) => {
      console.log(b);
      console.log(b.id);
      if(b.id === "Standard declarations"){
        toReturn = b;
        return;
      }
      nodeToArray(b.block).forEach((b2) => {
        if(b2.id === "Standard declarations"){
          toReturn = b2;
          return;
        }
        nodeToArray(b2.block).forEach((b3) => {
          if(b.id === "Standard declarations"){
            toReturn = b3;
            return;
          }
        });
      });
    });
    return toReturn;
  }
}
