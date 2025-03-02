(ns terminal.core
  (:require [cljfx.api :as fx]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [pyjama.core]))


(def state
  (atom
    {
     :url    "http://localhost:11434"
     :input  ""
     :history []
     :show-history? true
     :output ""}))

;; Get system information
(defn get-system-info []
  (let [os (System/getProperty "os.name")
        user (System/getProperty "user.name")
        home (System/getProperty "user.home")
        temp (System/getProperty "java.io.tmpdir")
        ip (-> (sh "hostname" "-I")
               :out
               (str/trim))
        java-version (System/getProperty "java.version")]
    {:os           os
     :user         user
     :home         home
     :temp         temp
     :ip           ip
     :java-version java-version}))

(defn model-call [input]
  (let [system-info (get-system-info)
        pre (format "You are on %s. The current user is %s. The home folder is %s. The temp folder is %s. The IP address is %s. The Java version is %s. Convert this human input to a bash command:
%%s
. Only output the bash code, nothing else."
                    (:os system-info)
                    (:user system-info)
                    (:home system-info)
                    (:temp system-info)
                    (:ip system-info)
                    (:java-version system-info))]
    (pyjama.core/ollama (:url @state)
                        :generate
                        {:pre    pre
                         :prompt input}
                        :response)))

(defn execute-command [command]
  (let [result (sh "bash" "-c" command)]
    (if (= 0 (:exit result))
      (:out result)
      (str "Error: " (:err result)))))

(defn root-view [{:keys [input output history show-history?]}]
  {:fx/type          :stage
   :showing          true
   :on-close-request (fn [_] (System/exit 0))
   :title            "Terminal App"
   :scene            {:fx/type     :scene
                      :stylesheets #{(.toExternalForm (io/resource "terminal.css"))}
                      :root        {:fx/type  :h-box  ;; Use h-box for left and right sections
                                    :children [{:fx/type  :v-box
                                                :h-box/hgrow   :always
                                                :children [{:fx/type   :text-area
                                                            :editable  false
                                                            :text      output
                                                            :wrap-text true
                                                            :v-box/vgrow     :always} ;; Allows resizing
                                                           {:fx/type         :text-area
                                                            :prompt-text     "Enter your command here"
                                                            :text            input
                                                            :on-text-changed (fn [event]
                                                                               (swap! state assoc :input event))
                                                            :v-box/vgrow          :always}
                                                           {:fx/type   :button
                                                            :text      "Execute"
                                                            :on-action (fn [_]
                                                                         (let [command (model-call input)
                                                                               result (execute-command command)]
                                                                           (swap! state update :history conj input)
                                                                           (swap! state assoc :output result)))}
                                                           {:fx/type   :button
                                                            :text      (if show-history? "Hide History" "Show History")
                                                            :on-action (fn [_]
                                                                         (swap! state update :show-history? not))}]}
                                               (if (not show-history?)
                                                 {:fx/type  :v-box
                                                  :pref-width 0
                                                  }
                                                 {:fx/type  :v-box
                                                  :pref-width 300
                                                  :children [{:fx/type :label
                                                              :text "Command History"}
                                                             {:fx/type  :v-box
                                                                        :children (map-indexed
                                                                                    (fn [idx cmd]
                                                                                      {:fx/type  :h-box
                                                                                       :children [{:fx/type :label
                                                                                                   :text    (str (inc idx) ". " cmd)}
                                                                                                  {:fx/type   :button
                                                                                                   :text      "Re-run"
                                                                                                   :on-action (fn [_]
                                                                                                                (swap! state assoc :input cmd))}]})
                                                                                    history)}]})]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type root-view)))

(defn -main [& _]
  (fx/mount-renderer state renderer))