$(function () {
    $('[data-toggle="tooltip"]').tooltip()

    let datasource = new Map()

    datasource.set("csv",{
        path: ["", "Location of the file"],
        header: ["true", "false", "Specify whether to consider the text header or add a personalized header"],
        delimiter: ["", "Delimiter of the columns"],
        mode: ["PERMISSIVE", "DROPMALFORMED", "FAILFAST", "Dealing with corrupt records during parsing"]
    }).set("parquet",{
            path: ["", "Location of the file"],
            spark_sql_parquet_filterPushdown: ["true", "false", "Enables Parquet filter push-down optimization when set to true."]
    }).set("mongodb",{
            url: ["", ""],
            database: ["", ""],
            collection: ["", ""]
    }).set("cassandra",{
        keyspace: ["", ""],
        table: ["", ""]
    }).set("jdbc",{
        url: ["", ""],
        driver: ["", ""],
        dbtable:["",""],
        user:["",""],
        password:["",""]
    })

    $('.addSrc').on('click', function (e) {
        $("#sourceModal").modal('toggle')
        const type = $(this).data("type");
        let data = datasource.get(type);
        const options = $("#options");
        options.html('');

        options.append("<span>Entity </span>")
        options.append("<span class='badge badge-pill badge-info' data-toggle='tooltip' data-placement='top' title='Enter a name of the entity you are about to add'> i</span>:")
        options.append("<input id='opt-entity' value='' class='form-control inpts' />")


        Object.entries(data).forEach(item=>{
            let index = item[0];
            let value = item[1];

            const indexBits = index.split("_");
            const option = indexBits[indexBits.length - 1];

            const descr = value[value.length - 1];
            options.append("<span>" + option + " </span>")
            options.append("<span class='badge badge-pill badge-info' data-toggle='tooltip' data-placement='top' title='" + descr + "'> i</span>:")

            if (value.length > 2) { // Multi-option dropbox
                options.append("<select id='opt-" + index + "' class='form-control inpts'>")
                for (let i = 0; i < value.length - 1; i++) {
                    $("#opt-" + index).append("<option value='" + value[i] + "'> " + value[i] + "</option>")
                }
            } else if (index === "path") { // select file
                options.append("<div id='pathSelector' style='width: 100%;'>" + // class='custom-file'
                    "<input type='input' id='opt-" + index + "-local' class='form-control inpts' style='width: 100%;'/>" +
                    "<span></span>" + //class='custom-file-control'
                    "</div>")
                options.append("</select>")
            } else
                options.append("<input id='opt-" + index + "' value='' class='form-control inpts' />")
        })
        $("#slctdSrc").val(type)
    })

    $('#saveSrc').on('click', function (e) {
        const slctdSrc = $("#slctdSrc").val();
        $("#sourceModal").modal('toggle')
        const optMap = new Map();
        $('#options > option:selected').each(function () {
            alert($(this).text())
            optMap.set($(this).parent().attr("id").split("-")[1], $(this).text());
        });

        // The text fields
        $('.inpts').each(function () {
            optMap.set($(this).attr("id").split("-")[1], $(this).val()); // Eliminate 'opt'
        });
        optMap.set("type", slctdSrc)

        let config = {
            options: {

            }
        }
        optMap.forEach((value, key) => {
            if (key === "type") {
                config[key] = value
            } else if (key === "path") {
                config["source"] = value
            } else if (key === "entity") {
                config[key] = value
            } else {
                config.options[key] = value
            }
        })
        $.ajax({
            method: "POST",
            url: "/setOptions",
            data: config
        }).done(function (data) {
            console.log(data)
            $("#options").html('');
        })
    })
})
