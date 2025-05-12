(ns pyjama.screens.core)


(defn settings-screen [state]
  {:fx/type   :v-box
   :alignment :center-left
   :spacing   10
   :padding   10
   :children
   [{:fx/type :label
     :text    "Settings"
     :style   {:-fx-font-size   20
               :-fx-font-weight "bold"}}
    {:fx/type :label :min-width 100 :text "URL"}
    {:fx/type   :text-field
     :min-width 400
     :text      (:url @state)
     :on-text-changed
     {:event/type :url-updated}
     }
    {:fx/type          :button
     :text             "Close"
     :on-mouse-clicked {:event/type :screen-settings/close}}
    ]})


(defn models-ps-view [{:keys [models]}]
  {:fx/type   :v-box
   :alignment :center-left
   :spacing   10
   :padding   10
   :children
   [{:fx/type :table-view
     :items   models
     :columns [{:fx/type            :table-column
                :text               "Name"
                :cell-value-factory #(-> % :name)}
               {:fx/type            :table-column
                :text               "Model"
                :cell-value-factory #(-> % :model)}
               {:fx/type            :table-column
                :text               "Size"
                :cell-value-factory #(-> % :size)}
               {:fx/type            :table-column
                :text               "Format"
                :cell-value-factory #(-> % :details :format)}
               {:fx/type            :table-column
                :text               "Parameter Size"
                :cell-value-factory #(-> % :details :parameter_size)}
               {:fx/type            :table-column
                :text               "Expires At"
                :cell-value-factory #(-> % :expires_at)}]}]})
