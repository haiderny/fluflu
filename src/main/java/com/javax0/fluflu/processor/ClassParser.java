package com.javax0.fluflu.processor;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic.Kind;

import com.javax0.aptools.Environment;
import com.javax0.aptools.FromThe;
import com.javax0.aptools.MethodTool;
import com.javax0.fluflu.Cloner;
import com.javax0.fluflu.Transition;
import com.javax0.fluflu.Transitions;

class ClassParser {
  final Element     classToFluentize;
  final ClassWriter classWriter;
  final String      core;
  String    clonerMethodName = null;

  ClassParser(Element classToFluentize, ClassWriter classWriter) {
    this.classToFluentize = classToFluentize;
    this.classWriter = classWriter;
    AnnotationMirror fluentize = FromThe.element(classToFluentize).getTheAnnotation("com.javax0.fluflu.Fluentize");
    String coreName = null;
    if (fluentize != null) {
      coreName = FromThe.annotation(fluentize).getStringValue("className");
    }
    if (coreName == null) {
      coreName = FromThe.element(classToFluentize).getClassName();
    }
    core = coreName;
  }

  private boolean isCloner(ExecutableElement method) {
    Cloner cloner = method.getAnnotation(Cloner.class);
    return cloner != null;
  }

  private Transition[] getTransitions(ExecutableElement method) {
    Transitions ts = method.getAnnotation(Transitions.class);
    final Transition[] transitions;
    if (ts == null) {
      Transition t = method.getAnnotation(Transition.class);
      if (t != null) {
        transitions = new Transition[1];
        transitions[0] = t;
      } else {
        transitions = null;
      }
    } else {
      transitions = ts.value();
    }
    return transitions;
  }

  private final Map<String, List<TransitionEdge>> transitionMap = new HashMap<>();

  private void processTransition(Transition transition, ExecutableElement methodElement) {
    final String[] fromStates = transition.from();
    final String toState;
    final boolean end = transition.end();
    if (end) {
      toState = FromThe.method(methodElement).getReturnType();
    } else {
      toState = transition.to();
    }
    final String name = transition.name();
    for (String fromState : fromStates) {
      if (!transitionMap.containsKey(fromState)) {
        transitionMap.put(fromState, new LinkedList<TransitionEdge>());
      }
      List<TransitionEdge> transitions = transitionMap.get(fromState);
      transitions.add(new TransitionEdge(methodElement, toState, name, end));
    }
  }

  private void processClonerMethod(ExecutableElement methodElement) {
    MethodTool method = FromThe.method(methodElement);
    if (method.getTheNumberOfParameters() > 0) {
      Environment.get().getMessager().printMessage(Kind.ERROR, "Cloner method should have no argument.");
    } else {
      clonerMethodName = method.getName();
    }
  }

  private void parse(ExecutableElement methodElement) {
    if (isCloner(methodElement)) {
      processClonerMethod(methodElement);
    }
    final Transition[] transitions = getTransitions(methodElement);
    if (transitions != null) {
      for (Transition transition : transitions) {
        processTransition(transition, methodElement);
      }
    }
  }

  private void generateStateClass(String state) throws IOException {
    String packageName = FromThe.element(classToFluentize).getPackageName();
    String className = FromThe.element(classToFluentize).getClassName();
    StateClassMaker maker = new StateClassMaker(packageName, state, core, className);
    String header = maker.generateStateClassHeader();
    StringBuilder body = new StringBuilder(header);
    for (TransitionEdge edge : transitionMap.get(state)) {
      body.append(maker.generateStateClassMethod(edge));
    }
    body.append(maker.generateStateClassFooter());
    classWriter.writeSource(packageName, state, body.toString());
  }

  private void generateStateClasses() throws IOException {
    for (String state : transitionMap.keySet()) {
      generateStateClass(state);
    }
  }

  void parse() throws IOException {
    for (ExecutableElement method : FromThe.element(classToFluentize).getMethods()) {
      parse(method);
    }
    generateStateClasses();
  }

}
