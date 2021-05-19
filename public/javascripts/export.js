$(function () {
    let mp = {}
    $("#generateMappings").click(function () {
        $.getJSON("/generateMappings", function (data) {
            mp = data;
            $(`#mappings-box`).show()
            $("#exportMappings").show()
            $("#mappings-box").html(mp.rml_text.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, "<br/>").replace(/\s/g, '&nbsp;&nbsp;&nbsp;'))
            $("#exportMappings").html("<br/><button type='button' class='btn btn-primary' id='saveMappings' style='margin-bottom: 10px;'>Export mappings</button>")
        })
    });
    $("#mappings").on("click", "button#saveMappings", function () {
        $.post("http://localhost:3000/generatemapping",
            {data: mp}, function (data) {
                console.log(data)
                if (data === true) {
                    alert("RDF generated successfully")
                }
            })
    });
});
