$(() => {
    $('[data-toggle="tooltip"]').tooltip()

    let datasource = new Map()

    datasource.set("csv", {
        Path: ["", "Location of the file"],
        Header: ["true", "false", "Specify whether to consider the text header or add a personalized header"],
        Delimiter: ["", "Delimiter of the columns"],
        Mode: ["PERMISSIVE", "DROPMALFORMED", "FAILFAST", "Dealing with corrupt records during parsing"]
    }).set("parquet", {
        Path: ["", "Location of the file"],
        Spark_sql_parquet_filterPushdown: ["true", "false", "Enables Parquet filter push-down optimization when set to true."]
    }).set("mongodb", {
        Url: ["", ""],
        Database: ["", ""],
        Collection: ["", ""]
    }).set("cassandra", {
        Keyspace: ["", ""],
        Table: ["", ""]
    }).set("jdbc", {
        Url: ["", ""],
        Driver: ["", ""],
        Dbtable: ["", ""],
        User: ["", ""],
        Password: ["", ""]
    })

    $('.addSrc').on('click', function (e) {
        $("#sourceModal").modal('show')
        const type = $(this).data("type");
        let data = datasource.get(type);
        let options = $("#options");
        options.html('');

        let temp = `<div class="input-group mb-3">
                    <span class="input-group-text">Entity</span>
                    <input type="text" id='opt-entity' value='' class='form-control inpts' required>
                    <span class="input-group-text" data-bs-toggle="tooltip" data-bs-placement="top" title="Enter a name of the entity you are about to add"><i class="fas fa-info"></i></span>
                </div>`
        Object.entries(data).forEach(item => {
            let index = item[0];
            let value = item[1];
            const indexBits = index.split("_");
            const option = indexBits[indexBits.length - 1];
            const descr = value[value.length - 1];
            let temp1 = ``;

            if (value.length > 2) {
                let mul = ``;
                let multioption = ``;
                value.forEach(function (val, index) {
                    if (index < value.length - 1)
                        multioption += `<option value="${val}">${val}</option>`
                })

                mul = `<select id='opt-${index}' class="form-select inpts" aria-label="Default select example">${multioption}</select>`

                temp1 = `<div class="input-group mb-3">
                <span class="input-group-text">${option}</span>
                ${mul}
                <span class="input-group-text" data-bs-toggle="tooltip" data-bs-placement="top" title="${descr}">
                      <i class="fas fa-info"></i>
                </span>
              </div>`

            } else {
                temp1 = `<div class="input-group mb-3">
                    <span class="input-group-text">${option}</span>
                    <input type="text" id="opt-${index}" value="" class="form-control inpts" required>
                    <span class="input-group-text" data-bs-toggle="tooltip" data-bs-placement="top" title="${descr}">
                           <i class="fas fa-info"></i>
                    </span>
                  </div>`
            }

            temp = temp + temp1;

        })
        options.html(temp)
        $("#slctdSrc").val(type)
    })


    let form = document.querySelectorAll('.needs-validation')

    // Loop over them and prevent submission
    Array.prototype.slice.call(form)
        .forEach(function (form) {
            document.getElementById("saveSrc")
                .addEventListener('click', function (event) {
                    if (!form.checkValidity()) {
                        event.preventDefault()
                        event.stopPropagation()
                    } else {
                        const slctdSrc = $("#slctdSrc").val();
                        const optMap = new Map();
                        $('#options > option:selected').each(function () {
                            alert($(this).text())
                            optMap.set($(this).parent().attr("id").split("-")[1], $(this).text());
                        });

                        $('.inpts').each(function () {
                            optMap.set($(this).attr("id").split("-")[1].toLowerCase(), $(this).val());
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
                            if(data){
                               $("#sourceModal").modal('hide')
                            }else{
                                $(".invalid-feedback").remove();
                                $('#opt-Path').val("")
                                $("#opt-Path").parent().append(
                                    '<div class="invalid-feedback">Please provide a valid file path. </div>'
                                );
                            }
                        })
                    }
                    form.classList.add('was-validated')
                }, false)
        })
})
