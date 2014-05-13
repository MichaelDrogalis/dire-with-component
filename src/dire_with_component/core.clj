(ns dire-with-component.core
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [dire.core :as dire]))

(defn handling-function [x]
  (println "Doing something with value:" x))

(defn event-loop [ch]
  (loop []
    (when-let [x (async/<!! ch)]
      (handling-function x)
      (recur))))

(defrecord EventHandler []
  component/Lifecycle
  
  (start [component]
    (println "Starting the Event Handler")

    (let [ch (async/chan 10)]
      (async/go (event-loop ch))
      (assoc component :ch ch)))

  (stop [component]
    (println "Stopping the Event Handler")
    (async/close! (:ch component))
    component))

(defrecord Logger []
  component/Lifecycle
  
  (start [component]
    (println "Starting the Logger")

    (let [logging-fn #(println "Logger: Found value" %)]
      (dire/with-pre-hook! #'handling-function logging-fn)
      (assoc component :logging-fn logging-fn)))

  (stop [component]
    (println "Stopping the Logger")
    (dire/remove-pre-hook! #'handling-function (:logging-fn component))
    component))

(defrecord Preconditions []
  component/Lifecycle

  (start [component]
    (println "Starting the Preconditions")

    (let [pre-fn #(not= 43 %)]
      (dire/with-precondition! #'handling-function :not-43 pre-fn)
      (assoc component :pre-fn pre-fn)))

  (stop [component]
    (println "Stopping the Preconditions")
    (dire/remove-precondition! #'handling-function (:pre-fn component))
    component))

(defrecord ErrorHandler []
  component/Lifecycle

  (start [component]
    (println "Starting the Error Handler")

    (let [error-handler-fn (fn [e & args] (println "Caught exception" e "for args" args))]
      (dire/with-handler! #'handling-function {:precondition :not-43} error-handler-fn)
      (assoc component :error-handler-fn error-handler-fn)))

  (stop [component]
    (println "Stopping the Error Handler")
    (dire/remove-handler! #'handling-function (:error-handler-fn component))
    component))

(def components [:event-handler :logger :preconditions :error-handler])

(defrecord EventingSystem [event-handler logger preconditions error-handler]
  component/Lifecycle
  (start [this]
    (component/start-system this components))
  (stop [this]
    (component/stop-system this components)))

(defn eventing-system []
  (map->EventingSystem
   {:event-handler (EventHandler.)
    :logger (component/using (Logger.) [:event-handler])
    :preconditions (component/using (Preconditions.) [:event-handler])
    :error-handler (component/using (ErrorHandler.) [:preconditions])}))

(def system (component/start (eventing-system)))

(async/>!! (:ch (:event-handler system)) 42)
(async/>!! (:ch (:event-handler system)) 43)
(async/>!! (:ch (:event-handler system)) 44)

(component/stop system)

