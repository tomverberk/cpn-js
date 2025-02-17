import {
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
} from "@angular/core";
import { Constants } from "../common/constants";
import TextRenderer from "../../lib/cpn-js/draw/TextRenderer";

import { Message } from "../common/message";
import { EventService } from "../services/event.service";
import { ModelService } from "../services/model.service";
import { element } from "protractor";
import { AccessCpnService } from "../services/access-cpn.service";
import { TabsContainer } from "src/lib/tabs/tabs-container/tabs.container";

@Component({
  selector: "app-properties-panel",
  templateUrl: "./properties-panel.component.html",
  styleUrls: ["./properties-panel.component.scss"],
})
export class PropertiesPanelComponent implements OnInit, OnDestroy {
  @ViewChild("tabsComponent") tabsComponent: TabsContainer;

  console = console;
  JSON = JSON;
  selectionProvider;
  projectData;

  tabList = [
    { id: "propertiesPanel", name: "Properties" },
    { id: "monitorPanel", name: "Monitor" },
    // { id: 'modelPanel', name: 'Model' },
  ];

  title = "";

  cpnElement;
  elementType;
  pageId;

  names = {
    name: "Name",
    initmark: "Initial marking",
    type: "Colorset",
    port: "Port type",
    cond: "Guard",
    time: "Time",
    code: "Action",
    priority: "Priority",
    subst: "Subpage",
    annot: "Annotation",
  };

  colors = [
    "#800000ff",
    "#ffff00ff",
    "#ffffffff",
    "#ff0000ff",
    "#c0c0c0ff",
    "#008080ff",
    "#000080ff",
    "#00ffffff",
    "#000000ff",
    "#808000ff",
    "#00ff00ff",
    "#808080ff",
    "#800080ff",
    "#008000ff",
    "#0000ffff",
  ];

  orientations = ["PtoT", "TtoP", "BOTHDIR"];

  portTypes = ["", "In", "Out", "In/Out"];

  pages = [];

  public layoutPartOpened: boolean[] = [];

  constructor(
    private eventService: EventService,
    private modelService: ModelService,
    public accessCpnService: AccessCpnService
  ) {}

  updateJsonScheduled = false;

  ngOnInit() {
    this.eventService.on(Message.PAGE_TAB_OPEN, (data) => {
      // console.log(this.constructor.name, 'Message.PAGE_OPEN, data = ', data);

      this.showPageAttrs(data.pageObject);
    });

    this.eventService.on(Message.SHAPE_SELECT, (data) => {
      console.log(this.constructor.name, "Message.SHAPE_SELECT, data = ", data);
      console.log("selected element", this.modelService.selectedElements);
      // this.selectTab('propertiesPanel');
      if (!data.element.length) {
        const element = data.element.labelTarget
          ? data.element.labelTarget.labelTarget || data.element.labelTarget
          : data.element;

        this.showShapeAttrs(element);
      } else {
        console.log("selectGroup");
      }
    });

    this.eventService.on(Message.MODEL_CHANGED, () => {
      console.log(this.constructor.name, "Message.MODEL_CHANGED");

      localStorage.setItem(
        "projectJson",
        JSON.stringify(this.modelService.projectData)
      );

      // if (!this.updateJsonScheduled) {
      //   this.updateJsonScheduled = true;
      //   setTimeout(() => {
      //     if (this.modelService.projectData
      //       && this.modelService.projectData.workspaceElements
      //       && this.modelService.projectData.workspaceElements.cpnet) {
      //       this.projectData = cloneObject(this.modelService.projectData.workspaceElements.cpnet);
      //       this.updateJsonScheduled = false;
      //     }
      //   }, 1000);
      // }
    });

    this.eventService.on(Message.MONITOR_OPEN, () => {
      this.selectTab("monitorPanel");
    });
  }

  ngOnDestroy() {}

  isSelectedElements() {
    return this.modelService.selectedElements.length > 0;
  }

  selectTab(tabId) {
    setTimeout(() => {
      const tab = this.tabsComponent.getTabByID(tabId);
      if (tab) {
        this.tabsComponent.selectTab(tab);
      }
    }, 0);
  }

  // getCpnElement() {
  //   return JSON.stringify(this.cpnElement);
  // }

  // onChange() {
  //   console.log('onChange(), this.cpnElement = ', this.cpnElement);
  // }

  updateChanges() {
    console.log("updateChanges(), this = ", this);
    if (this.cpnElement && this.cpnElement.type) {
      this.modelService.fixPlaceInitmark(this.cpnElement);
    }
    if (this.cpnElement && this.cpnElement.annot) {
      this.modelService.fixArcAnnot(this.cpnElement);
    }

    this.eventService.send("editing.cancel");
    this.eventService.send(Message.MODEL_UPDATE_DIAGRAM, {
      cpnElement: this.cpnElement,
    });
    // this.eventService.send(Message.MODEL_CHANGED);
  }

  updateGroupChanges(field, attr) {
    const self = this;
    const template = this.modelService.selectedElements[0].cpnElement;
    this.modelService.selectedElements.forEach((value) => {
      value.cpnElement[field][attr] = template[field][attr];
      self.cpnElement = value.cpnElement;
      self.updateChanges();
    });

    const arcs = this.modelService.getArcsForElements(
      this.modelService.selectedElements.map((value) => {
        return value.cpnElement;
      })
    );

    arcs.forEach((value) => {
      const arc = value;
      arc[field][attr] = template[field][attr];
      self.cpnElement = arc;
      self.updateChanges();
    });
    // self.updateChanges();
    this.cpnElement = template;
  }

  getGroupColorValue(field) {
    if (this.modelService.selectedElements) {
      // @ts-ignore
      return this.modelService.selectedElements[0].cpnElement[field];
    } else {
      return undefined;
    }
  }

  updateLabel(event) {
    console.log(
      "updateLabel(), event, this.cpnElement = ",
      event,
      this.cpnElement
    );

    this.updateChanges();
  }

  clearData() {
    this.title = "";
    this.layoutPartOpened = [];
    this.layoutPartOpened["common"] = true;
    this.cpnElement = null;
  }

  /**
   * Forms a type of model attributes convenient for display on a component.
   * @param pageObject - model page object
   */
  showPageAttrs(pageObject) {
    console.log(
      this.constructor.name,
      "showPageAttrs(), pageObject = ",
      pageObject
    );

    this.clearData();

    this.title = "Page" + " (id = " + pageObject._id + ")";
    this.pageId = pageObject._id;
  }

  /**
   * function that fills the data setd for fields in the properties panel
   * @param shapeObject - model shape object
   */
  showShapeAttrs(shapeObject) {
    console.log(
      this.constructor.name,
      "showShapeAttrs(), shapeObject = ",
      shapeObject
    );

    this.clearData();

    if (!shapeObject || !shapeObject.cpnElement) {
      return;
    }

    let type = shapeObject.type;
    type = type.replace(/^cpn:/, "");

    if (["Place", "Transition", "Connection", "Label"].indexOf(type) < 0) {
      return;
    }

    this.elementType = shapeObject.type;
    this.cpnElement = shapeObject.cpnElement;
    this.title = type;
  }

  /**
   * Get list of names for pages wich is not current or not subpage
   */
  getSubstPages(cpnElement) {
    // console.log('getSubstPages()');
    const pageList = this.modelService.getAllPages();

    // console.log('getSubstPages(), pageList = ', pageList);

    const subPageIdList = [];
    const parentPageIdList = [];

    for (const page of pageList) {
      const transList = page.trans instanceof Array ? page.trans : [page.trans];
      for (const trans of transList) {
        if (trans && trans.subst && trans.subst._subpage) {
          if (page._id !== this.pageId) {
            subPageIdList.push(trans.subst._subpage);
          }

          if (
            trans.subst._subpage === this.pageId ||
            parentPageIdList.includes(trans.subst._subpage)
          ) {
            parentPageIdList.push(page._id);
          }
        }
      }
    }

    // console.log('getSubstPages(), subPageIdList = ', subPageIdList);

    const pageNames = ["-- empty --"];
    for (const page of pageList) {
      if (
        page._id !== this.pageId &&
        ((!subPageIdList.includes(page._id) &&
          !parentPageIdList.includes(page._id)) ||
          subPageIdList.includes(page._id))
      ) {
        pageNames.push(page.pageattr._name);
      }
    }
    return pageNames;
  }

  getBindTransElementSubst(cpnElement) {
    const bindObj = this.modelService.getArcEnds(cpnElement);
    if (bindObj && bindObj.trans) {
      return bindObj.trans.subst;
    } else {
      return false;
    }
  }

  getSubPagesPorts(cpnElement, transEnd) {
    // console.log('getSubPagesPorts(), transEnd = ', transEnd);

    const bindObj = this.modelService.getArcEnds(cpnElement);

    if (!bindObj) {
      return undefined;
    }

    const ports = this.modelService.getAllPorts(cpnElement, bindObj.trans);
    const portNames = [""];
    for (const port of ports) {
      portNames.push(port.text);
    }
    return portNames;
  }

  getColorsetList() {
    let list = this.modelService.getStandardDeclarations().map((el) => el.id);
    list = list.sort((a, b) => {
      if (a < b) {
        return -1;
      }
      if (a > b) {
        return 1;
      }
      return 0;
    });
    return list;
  }

  updateColorset(event) {
    this.updateChanges();
  }

  getPort(cpnElement) {
    return cpnElement.port
      ? {
          value:
            cpnElement.port._type === "I/O" ? "In/Out" : cpnElement.port._type,
        }
      : { value: "" };
  }

  getPortBind(cpnElement) {
    const bindObj = this.modelService.getArcEnds(cpnElement);
    if (bindObj && bindObj.trans.subst && bindObj.trans.subst._portsock) {
      const ids = this.parsePortSock(bindObj.trans.subst._portsock);
      for (const pair of ids) {
        if (pair.includes(cpnElement.placeend._idref)) {
          for (const id of pair) {
            if (id !== cpnElement.placeend._idref) {
              return {
                value: this.modelService.getPortNameById(
                  bindObj.trans.subst._subpage,
                  id
                ),
              };
            }
          }
        }
      }
    }
    return { value: "" };
  }

  parsePortSock(portsock) {
    let str = portsock.trim().replace(new RegExp(/[),(]/, "g"), "-");
    str = str.substr(1, str.length - 2);
    // let ids = str.split(new RegExp(/-+/, 'g'))
    const ids = [];
    for (const el of str.split("--")) {
      ids.push(el.split("-"));
    }
    return ids;
  }

  updatePortBind(event) {
    console.log("updatePortBind    ", event);
    const bindObj = this.modelService.getArcEnds(this.cpnElement);

    if (!bindObj) {
      this.updateChanges();
      return;
    }

    const id = this.modelService.getPortIdByName(
      bindObj.trans.subst._subpage,
      event,
      bindObj.orient
    );

    let ids;
    if (bindObj.trans.subst._portsock !== "") {
      ids = this.parsePortSock(bindObj.trans.subst._portsock);
      const pair = ids.find((p) => {
        return p.includes(bindObj.place._id);
      });

      if (pair) {
        if (id) {
          pair[0] = id.trim();
          pair[1] = bindObj.place._id.trim();
        } else {
          ids = ids.filter((e) => e[0] !== pair[0] || e[1] !== pair[1]);
        }
      } else {
        ids.push([id, bindObj.place._id]);
      }
    } else {
      ids = [[id.trim(), bindObj.place._id.trim()]];
    }

    let portsock = "";
    for (const bind of ids) {
      portsock += "(" + bind[0] + "," + bind[1] + ")";
    }
    bindObj.trans.subst._portsock = portsock;

    this.updateChanges();
  }

  getSubst(cpnElement) {
    return cpnElement.subst && cpnElement.subst.subpageinfo
      ? { value: cpnElement.subst.subpageinfo._name }
      : { value: "" };
  }

  getInstanceId(cpnElement) {
    const inst = this.modelService.getInstance(this.pageId);
    return inst ? inst._id : "not defined";
  }

  updatePortType(event) {
    // console.log(this.constructor.name, 'updatePortType(), event = ', event);

    const portType = event.trim();

    if (!this.cpnElement) {
      return;
    }

    if (portType === "") {
      delete this.cpnElement.port;
      this.updateChanges();
      return;
    }

    if (!this.cpnElement.port) {
      this.cpnElement.port = this.modelService.createPortObject(
        this.cpnElement,
        portType
      );
    }

    this.cpnElement.port.text = portType;
    this.cpnElement.port._type = portType === "In/Out" ? "I/O" : portType;

    // console.log(this.constructor.name, 'updatePortType(), this.cpnElement = ', this.cpnElement);

    this.updateChanges();
  }

  updateSubst(event) {
    // console.log(this.constructor.name, 'updateSubst(), event = ', event);

    const pageName = event.trim();

    if (!this.cpnElement) {
      return;
    }

    if (pageName === "" || pageName === "-- empty --") {
      delete this.cpnElement.subst;
    } else {
      const pageId = this.modelService.getPageId(pageName);

      // create/update subst
      if (!this.cpnElement.subst) {
        this.cpnElement.subst = this.modelService.createSubstObject(
          this.cpnElement,
          pageName,
          pageId
        );
      }

      this.cpnElement.subst.subpageinfo.text = pageName;
      this.cpnElement.subst.subpageinfo._name = pageName;
      this.cpnElement.subst._subpage = pageId;

      // console.log(this.constructor.name, 'updateSubst(), this.cpnElement = ', this.cpnElement);
    }

    // update instances
    this.modelService.updateInstances();

    this.updateChanges();

    this.eventService.send(Message.TREE_UPDATE_PAGES, {
      currentPageId: this.pageId,
    });
  }
}
