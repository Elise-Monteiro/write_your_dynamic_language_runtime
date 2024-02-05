package fr.umlv.smalljs.astinterp;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.Block;
import fr.umlv.smalljs.ast.Expr.FieldAccess;
import fr.umlv.smalljs.ast.Expr.FieldAssignment;
import fr.umlv.smalljs.ast.Expr.Fun;
import fr.umlv.smalljs.ast.Expr.FunCall;
import fr.umlv.smalljs.ast.Expr.If;
import fr.umlv.smalljs.ast.Expr.Literal;
import fr.umlv.smalljs.ast.Expr.LocalVarAccess;
import fr.umlv.smalljs.ast.Expr.LocalVarAssignment;
import fr.umlv.smalljs.ast.Expr.MethodCall;
import fr.umlv.smalljs.ast.Expr.New;
import fr.umlv.smalljs.ast.Expr.Return;
import fr.umlv.smalljs.ast.Script;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;
import fr.umlv.smalljs.rt.JSObject.Invoker;

import java.io.PrintStream;
import java.util.*;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.util.stream.Collectors.joining;

public class ASTInterpreter {
  private static JSObject asJSObject(Object value, int lineNumber) {
    if (!(value instanceof JSObject jsObject)) {
      throw new Failure("at line " + lineNumber + ", type error " + value + " is not a JSObject");
    }
    return jsObject;
  }

  static Object visit(Expr expression, JSObject env) {//on return pas on yield
    return switch (expression) {
      case Block(List<Expr> instrs, int lineNumber) -> {//avec prof
        //throw new UnsupportedOperationException("TODO Block");
        for(var instr: instrs) {
          visit(instr, env);
        }
        yield UNDEFINED;
      }
      case Literal<?>(Object value, int lineNumber) -> { //on pourrait se contenter de fleche value pas besoin d'ouvrir de block//avec prof
        //throw new UnsupportedOperationException("TODO Literal");
        yield value;
      }
      case FunCall(Expr qualifier, List<Expr> args, int lineNumber) -> {//() c'est qualifier//avec prof
        //throw new UnsupportedOperationException("TODO FunCall");
        var mayBeFunction = visit(qualifier, env);
          if (!(mayBeFunction instanceof JSObject jsObject)) {
            throw new Failure("not a funtion " + mayBeFunction + " at " + lineNumber);
          }
          var values = args.stream().map(arg -> visit(arg, env)).toArray();
          yield jsObject.invoke(UNDEFINED, values);
      }
      case LocalVarAccess(String name, int lineNumber) -> {//avec prof
        //throw new UnsupportedOperationException("TODO LocalVarAccess");
        yield env.lookup(name);//lookup écrit comme en javascript renvoie undefined et on veut qu'elle renvoie undefined
      }
      case LocalVarAssignment(String name, Expr expr, boolean declaration, int lineNumber) -> {//tout seul corriger pas prof//faire dans ce sens à cause de var  a = a;
        //throw new UnsupportedOperationException("TODO LocalVarAssignment");
        var valueOrUndefined = env.lookup(name);
        if(declaration && valueOrUndefined != UNDEFINED) {
          throw new Failure("variable " + name + " is defined twice at " + lineNumber);
        }
        /*if(!declaration && valueOrUndefined == UNDEFINED) {//prof pas mis
          throw new Failure("variable " + name + " is undefined at " + lineNumber);
        }*/
        var result = visit(expr, env);
        env.register(name, result);
        yield result;
      }
      case Fun(Optional<String> optName, List<String> parameters, Block body, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO Fun");
        var functionName = optName.orElse("lambda");//si pas de nom on l'appelle lambda
        Invoker invoker = new Invoker() {
          @Override
          public Object invoke(JSObject self, Object receiver, Object... args) {
              // check the arguments length
              if(args.length != parameters.size()) {
                throw new Failure("wrong nomber or arguments " + args.length + "(should be " + parameters.size()
                  + ") at " + lineNumber);
              }
              // create a new environment
              var newEnv = JSObject.newEnv(env);//car enfant à acces au parent//dans la vrai vie on variable sur la pile
              // add this and all the parameters
              newEnv.register("this", receiver);
              for (int i = 0; i < parameters.size(); i++) {
                  var parameter = parameters.get(i);//probleme si liste chainer il aurait fallu vérifier
                  newEnv.register(parameter, args[i]);
              }
              // visit the body
              try {
                return visit(body, newEnv);
              } catch (ReturnError error) {
                return error.getValue();
              }
            }
          };
        // create the JS function with the invoker
        var function = JSObject.newFunction(functionName, invoker);
        // register it if necessary //on enregistre pas si il n'a pas de nom
        optName.ifPresent(name -> env.register(name, function));
        // yield the function
        yield function;
      }
      case Return(Expr expr, int lineNumber) -> {//faut arreter l'appelle recursive//pas à faire vrai vie car obliger peter une exception
        //throw new UnsupportedOperationException("TODO Return");
        throw new ReturnError(visit(expr, env));//si on leve error rarement attrapé et pas besoin de les attrapé
      }
      case If(Expr condition, Block trueBlock, Block falseBlock, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO If");
        var value = visit(condition, env);
        if(value instanceof Integer i && i == 0) {
          yield visit(falseBlock,env);
        }
        yield visit(trueBlock,env);
      }
      case New(Map<String, Expr> initMap, int lineNumber) -> {//a faire
        //throw new UnsupportedOperationException("TODO New");
        var newObject = JSObject.newObject(null);
        for(var e: initMap.entrySet()) {
          var result = visit(e.getValue(), env);
          newObject.register(e.getKey(), result);
        }
        yield newObject;
      }
      case FieldAccess(Expr receiver, String name, int lineNumber) -> {//a faire
        //throw new UnsupportedOperationException("TODO FieldAccess");
        var mayBeObject = visit(receiver, env);
        if (!(mayBeObject instanceof JSObject jsObject)) {
          throw new Failure("not a variable " + mayBeObject + " at " + lineNumber);
        }
        yield ((JSObject) mayBeObject).lookup(name);
      }
      case FieldAssignment(Expr receiver, String name, Expr expr, int lineNumber) -> {//a faire
        //throw new UnsupportedOperationException("TODO FieldAssignment");
        var mayBeObject = visit(receiver, env);
        if (!(mayBeObject instanceof JSObject jsObject)) {
          throw new Failure("not a variable " + mayBeObject + " at " + lineNumber);
        }
        var result = visit(expr, env);
        ((JSObject) mayBeObject).register(name, result);
        yield mayBeObject;
      }
      case MethodCall(Expr receiver, String name, List<Expr> args, int lineNumber) -> {//pas a faire
        throw new UnsupportedOperationException("TODO MethodCall");
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static void interpret(Script script, PrintStream outStream) {
    JSObject globalEnv = JSObject.newEnv(null);
    Block body = script.body();
    globalEnv.register("global", globalEnv);
    globalEnv.register("print", JSObject.newFunction("print", (self, receiver, args) -> {
      System.err.println("print called with " + Arrays.toString(args));
      outStream.println(Arrays.stream(args).map(Object::toString).collect(joining(" ")));
      return UNDEFINED;
    }));
    globalEnv.register("+", JSObject.newFunction("+", (self, receiver, args) -> (Integer) args[0] + (Integer) args[1]));
    globalEnv.register("-", JSObject.newFunction("-", (self, receiver, args) -> (Integer) args[0] - (Integer) args[1]));
    globalEnv.register("/", JSObject.newFunction("/", (self, receiver, args) -> (Integer) args[0] / (Integer) args[1]));
    globalEnv.register("*", JSObject.newFunction("*", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));
    globalEnv.register("%", JSObject.newFunction("%", (self, receiver, args) -> (Integer) args[0] % (Integer) args[1]));

    globalEnv.register("==", JSObject.newFunction("==", (self, receiver, args) -> args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("!=", JSObject.newFunction("!=", (self, receiver, args) -> !args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("<", JSObject.newFunction("<", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
    globalEnv.register("<=", JSObject.newFunction("<=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
    globalEnv.register(">", JSObject.newFunction(">", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
    globalEnv.register(">=", JSObject.newFunction(">=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
    visit(body, globalEnv);
  }
}

